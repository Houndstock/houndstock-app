package com.houndstock.backend.scraper.ppfas

import com.houndstock.backend.scraper.model.HoldingSection
import com.houndstock.backend.scraper.model.SchemePortfolio
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Parses the real PPFAS April 2026 monthly portfolio file (committed as a
 * test fixture) and asserts the data we'd already inspected by hand.
 *
 * If PPFAS changes their workbook layout, this test breaks first — and the
 * fixture is small enough (~440 KB) to keep in-repo for that signal.
 */
class PpfasParserTest {

    private val workbookBytes: ByteArray by lazy {
        val stream = javaClass.classLoader
            .getResourceAsStream("fixtures/ppfas_apr2026.xlsx")
            ?: error("Missing test fixture: fixtures/ppfas_apr2026.xlsx")
        stream.use { it.readBytes() }
    }

    @Test
    fun `parses all seven PPFAS scheme sheets`() {
        val portfolios = PpfasParser().parse(workbookBytes)
        // Apr 2026 workbook has: PPFCF, PPLF, PPTSF, PPCHF, PPAF, PPDAAF, PPLCF
        assertEquals(7, portfolios.size, "expected 7 schemes, got ${portfolios.map { it.sheetCode }}")
        val codes = portfolios.map { it.sheetCode }.toSet()
        assertTrue(codes.contains("PPFCF"), "missing Flexi Cap sheet (PPFCF)")
        assertTrue(codes.contains("PPLF"),  "missing Liquid sheet (PPLF)")
        assertTrue(codes.contains("PPTSF"), "missing Tax Saver sheet (PPTSF)")
    }

    @Test
    fun `flexi cap portfolio contains HDFC Bank at expected weight`() {
        val portfolios = PpfasParser().parse(workbookBytes)
        val flexi = portfolios.byCode("PPFCF")
        assertEquals(LocalDate(2026, 4, 30), flexi.asOfDate)
        assertTrue(
            flexi.schemeName.contains("Flexi Cap", ignoreCase = true),
            "scheme name was '${flexi.schemeName}'",
        )

        val hdfc = flexi.holdings.firstOrNull { it.isin == "INE040A01034" }
        assertNotNull(hdfc, "HDFC Bank not found in Flexi Cap holdings")
        hdfc!!
        assertTrue(
            hdfc.instrumentName.startsWith("HDFC Bank", ignoreCase = true),
            "name was '${hdfc.instrumentName}'",
        )
        assertEquals("Banks", hdfc.industry)
        assertEquals(HoldingSection.EQUITY_LISTED, hdfc.section)

        // Source stores % as a fraction (0.0794 = 7.94%). Allow a tiny tolerance
        // for floating-point comparisons coming out of POI.
        val weight = hdfc.weightFraction
        assertTrue(
            (weight - BigDecimal("0.0794")).abs() < BigDecimal("0.0001"),
            "weight fraction was $weight (expected ~0.0794)",
        )
    }

    @Test
    fun `holdings exclude header sub-total and total rows`() {
        val portfolios = PpfasParser().parse(workbookBytes)
        // Every parsed holding must have a real ISIN — header rows have a
        // blank ISIN cell and must be filtered out.
        val nonIsin = portfolios.flatMap { it.holdings }
            .filter { it.isin.isBlank() || !ISIN_RE.matches(it.isin) }
        assertEquals(emptyList<Any>(), nonIsin)

        // Every weight fraction must be in (0, 1]; a > 1 value would indicate
        // we accidentally captured a Total / Sub Total row written as percent.
        val outOfRange = portfolios.flatMap { it.holdings }
            .filter { it.weightFraction <= BigDecimal.ZERO || it.weightFraction > BigDecimal.ONE }
        assertEquals(
            emptyList<Any>(),
            outOfRange,
            "found ${outOfRange.size} holdings with implausible weights",
        )
    }

    @Test
    fun `flexi cap has a reasonable number of equity holdings`() {
        val portfolios = PpfasParser().parse(workbookBytes)
        val flexi = portfolios.byCode("PPFCF")
        val equityCount = flexi.holdings.count {
            it.section == HoldingSection.EQUITY_LISTED ||
                it.section == HoldingSection.EQUITY_FOREIGN
        }
        // Flexi Cap typically holds 20-40 equity positions. A wildly different
        // number means the parser misclassified rows.
        assertTrue(
            equityCount in 15..80,
            "expected 15..80 equity holdings, got $equityCount",
        )
    }

    private fun List<SchemePortfolio>.byCode(code: String): SchemePortfolio =
        firstOrNull { it.sheetCode == code }
            ?: error("portfolio with sheet code '$code' not found (have: ${map { it.sheetCode }})")

    private companion object {
        private val ISIN_RE = Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]")
    }
}
