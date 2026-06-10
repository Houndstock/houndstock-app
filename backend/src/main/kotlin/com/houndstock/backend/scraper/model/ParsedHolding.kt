package com.houndstock.backend.scraper.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * One scheme's portfolio for one as-of date, parsed from an AMC's monthly
 * disclosure file. Pure data — independent of the data source or DB.
 */
data class SchemePortfolio(
    /** AMC's short code for the scheme (e.g. "PPFCF" for Parag Parikh Flexi Cap). */
    val sheetCode: String,
    /** Full scheme name as it appears on the AMC's sheet. */
    val schemeName: String,
    val asOfDate: LocalDate,
    val holdings: List<ParsedHolding>,
)

data class ParsedHolding(
    val instrumentName: String,
    val isin: String,
    /** Industry or rating tag from the source. May be null for cash/derivative rows. */
    val industry: String?,
    val quantity: Long?,
    val marketValueLakhs: BigDecimal,
    /**
     * % of net assets as stored in the source file. PPFAS stores it as a
     * decimal fraction (0.0794 means 7.94%) — we preserve that scale here
     * and let the display layer multiply by 100.
     */
    val weightFraction: BigDecimal,
    val section: HoldingSection,
)

enum class HoldingSection {
    EQUITY_LISTED,
    EQUITY_UNLISTED,
    EQUITY_FOREIGN,
    DEBT_LISTED,
    DEBT_UNLISTED,
    DEBT_MONEY_MARKET,
    DERIVATIVES,
    CASH_AND_EQUIVALENTS,
    REITS_INVITS,
    OTHER,
}
