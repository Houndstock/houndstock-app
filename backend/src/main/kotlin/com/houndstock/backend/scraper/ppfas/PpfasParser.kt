package com.houndstock.backend.scraper.ppfas

import com.houndstock.backend.scraper.model.HoldingSection
import com.houndstock.backend.scraper.model.ParsedHolding
import com.houndstock.backend.scraper.model.SchemePortfolio
import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.regex.Pattern

/**
 * Parses a PPFAS monthly portfolio workbook.
 *
 * Structure (verified against Apr 2026 file):
 *  - One sheet per scheme. Sheet name = scheme short code (e.g. PPFCF).
 *  - Row 0 = full scheme name in column B (e.g. "Parag Parikh Flexi Cap Fund...").
 *  - Row 2 = "Monthly Portfolio Statement as on <date>" in column B.
 *  - Row 3 = column headers in column B..H.
 *  - Row 4+ = section headers, sub-section headers, holdings, sub-totals, totals.
 *    Holdings are identified by a 12-char ISIN in column C; everything else is skipped.
 *  - The "current section" walks down the sheet as we hit recognizable header rows
 *    ("Equity & Equity related", "Debt Instruments", "Foreign Securities", etc.)
 *    plus sub-headers ("(a) Listed / awaiting listing", "(b) Privately placed / Unlisted").
 */
class PpfasParser {
    private val log = LoggerFactory.getLogger(javaClass)

    fun parse(workbookBytes: ByteArray): List<SchemePortfolio> {
        return XSSFWorkbook(ByteArrayInputStream(workbookBytes)).use { wb ->
            (0 until wb.numberOfSheets).mapNotNull { idx ->
                parseSheet(wb.getSheetAt(idx))
            }
        }
    }

    private fun parseSheet(sheet: Sheet): SchemePortfolio? {
        val sheetCode = sheet.sheetName.trim()
        val schemeName = sheet.getRow(0)?.let { firstNonBlankString(it) }?.trim()
            ?: return null.also { log.warn("Sheet $sheetCode has no scheme name in row 0; skipping") }
        val asOfDate = sheet.getRow(2)?.let { extractAsOfDate(firstNonBlankString(it).orEmpty()) }
            ?: return null.also { log.warn("Sheet $sheetCode has no parseable as-of date in row 2; skipping") }

        val holdings = mutableListOf<ParsedHolding>()
        var currentTopSection: TopSection? = null
        var currentSubSection: SubSection? = null

        // Holdings rows live below row 3 (headers). Iterate everything from row 4.
        for (row in sheet) {
            if (row.rowNum < 4) continue
            // Determine if this row is a section header, sub-section, holding, or noise.
            val colB = stringCell(row, 1)?.trim().orEmpty()
            val isin = stringCell(row, 2)?.trim().orEmpty()

            val topMatch = TopSection.match(colB)
            if (topMatch != null) {
                currentTopSection = topMatch
                currentSubSection = null
                continue
            }
            val subMatch = SubSection.match(colB)
            if (subMatch != null) {
                currentSubSection = subMatch
                continue
            }

            // Valid holdings rows have an ISIN in column C.
            if (!ISIN_REGEX.matcher(isin).matches()) continue
            if (colB.isBlank()) continue

            val name = colB
            val industry = stringCell(row, 3)?.trim()
            val quantity = numericCell(row, 4)?.toLong()
            val marketValueLakhs = numericCell(row, 5) ?: continue
            val weightFraction = numericCell(row, 6) ?: continue
            val section = sectionFor(currentTopSection, currentSubSection)

            holdings += ParsedHolding(
                instrumentName = name,
                isin = isin.uppercase(),
                industry = industry,
                quantity = quantity,
                marketValueLakhs = marketValueLakhs,
                weightFraction = weightFraction,
                section = section,
            )
        }

        log.info("Parsed sheet $sheetCode: ${holdings.size} holdings as of $asOfDate")
        return SchemePortfolio(
            sheetCode = sheetCode,
            schemeName = schemeName,
            asOfDate = asOfDate,
            holdings = holdings,
        )
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun firstNonBlankString(row: Row): String? {
        for (cell in row) {
            val s = stringCell(cell)?.trim().orEmpty()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun stringCell(row: Row, col: Int): String? = stringCell(row.getCell(col))

    private fun stringCell(cell: Cell?): String? = when (cell?.cellType) {
        null, CellType.BLANK, CellType.ERROR -> null
        CellType.STRING -> cell.stringCellValue
        CellType.NUMERIC -> cell.numericCellValue.toString()
        CellType.BOOLEAN -> cell.booleanCellValue.toString()
        CellType.FORMULA -> when (cell.cachedFormulaResultType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            else -> null
        }
        else -> null
    }

    private fun numericCell(row: Row, col: Int): BigDecimal? {
        val cell = row.getCell(col) ?: return null
        return when (cell.cellType) {
            CellType.NUMERIC -> BigDecimal.valueOf(cell.numericCellValue)
            CellType.STRING -> cell.stringCellValue.trim().toBigDecimalOrNull()
            CellType.FORMULA -> when (cell.cachedFormulaResultType) {
                CellType.NUMERIC -> BigDecimal.valueOf(cell.numericCellValue)
                CellType.STRING -> cell.stringCellValue.trim().toBigDecimalOrNull()
                else -> null
            }
            else -> null
        }
    }

    private fun extractAsOfDate(rowText: String): LocalDate? {
        // Examples: "Monthly Portfolio Statement as on April 30, 2026"
        val match = DATE_REGEX.matcher(rowText)
        if (!match.find()) return null
        val monthName = match.group(1)
        val day = match.group(2).toInt()
        val year = match.group(3).toInt()
        val month = MONTHS[monthName.lowercase()] ?: return null
        return LocalDate(year, month, day)
    }

    private fun sectionFor(top: TopSection?, sub: SubSection?): HoldingSection {
        return when (top) {
            TopSection.EQUITY -> when (sub) {
                SubSection.LISTED, null -> HoldingSection.EQUITY_LISTED
                SubSection.UNLISTED -> HoldingSection.EQUITY_UNLISTED
            }
            TopSection.FOREIGN -> HoldingSection.EQUITY_FOREIGN
            TopSection.DEBT -> when (sub) {
                SubSection.LISTED, null -> HoldingSection.DEBT_LISTED
                SubSection.UNLISTED -> HoldingSection.DEBT_UNLISTED
            }
            TopSection.MONEY_MARKET -> HoldingSection.DEBT_MONEY_MARKET
            TopSection.DERIVATIVES -> HoldingSection.DERIVATIVES
            TopSection.CASH -> HoldingSection.CASH_AND_EQUIVALENTS
            TopSection.REITS_INVITS -> HoldingSection.REITS_INVITS
            null -> HoldingSection.OTHER
        }
    }

    private enum class TopSection {
        EQUITY, FOREIGN, DEBT, MONEY_MARKET, DERIVATIVES, CASH, REITS_INVITS;

        companion object {
            fun match(text: String): TopSection? {
                val t = text.lowercase()
                return when {
                    t.startsWith("equity & equity related") -> EQUITY
                    t.startsWith("foreign securities") || t.startsWith("foreign equity") -> FOREIGN
                    t.startsWith("debt instruments") || t.startsWith("debt and money market") -> DEBT
                    t.startsWith("money market instruments") -> MONEY_MARKET
                    t.startsWith("derivatives") || t.contains("futures") || t.contains("options") -> DERIVATIVES
                    t.startsWith("cash") || t.startsWith("trep") || t.startsWith("net receivables") -> CASH
                    t.startsWith("reits") || t.startsWith("invits") -> REITS_INVITS
                    else -> null
                }
            }
        }
    }

    private enum class SubSection {
        LISTED, UNLISTED;

        companion object {
            fun match(text: String): SubSection? {
                val t = text.lowercase()
                return when {
                    t.startsWith("(a) listed") || t.startsWith("(a) awaiting") -> LISTED
                    t.startsWith("(b) privately") || t.startsWith("(b) unlisted") -> UNLISTED
                    else -> null
                }
            }
        }
    }

    companion object {
        private val ISIN_REGEX = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]")
        private val DATE_REGEX = Pattern.compile(
            "as on ([A-Za-z]+) (\\d{1,2}),? (\\d{4})",
            Pattern.CASE_INSENSITIVE,
        )
        private val MONTHS = mapOf(
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "may" to 5, "june" to 6, "july" to 7, "august" to 8,
            "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        )
    }
}
