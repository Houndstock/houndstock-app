package com.houndstock.backend.scraper.ppfas

import kotlinx.datetime.LocalDate

/**
 * PPFAS publishes monthly portfolios at:
 *   https://amc.ppfas.com/downloads/portfolio-disclosure/<YEAR>/
 *     PPFAS_Monthly_Portfolio_Report_<MonthName>_<LastDay>_<Year>.<ext>
 *
 * The extension alternates between .xls and .xlsx month-to-month (the file
 * is actually XLSX internally either way). Callers should try both.
 */
object PpfasUrlBuilder {

    private val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /** Last day of [month] in [year], Gregorian. */
    fun lastDayOfMonth(year: Int, month: Int): Int {
        require(month in 1..12) { "month must be 1..12, was $month" }
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> error("unreachable")
        }
    }

    private fun isLeapYear(y: Int) = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)

    fun candidateUrls(year: Int, month: Int): List<String> {
        require(month in 1..12)
        val day = lastDayOfMonth(year, month)
        val monthName = monthNames[month - 1]
        val base = "https://amc.ppfas.com/downloads/portfolio-disclosure/$year/" +
            "PPFAS_Monthly_Portfolio_Report_${monthName}_${day}_$year"
        return listOf("$base.xls", "$base.xlsx")
    }

    fun expectedAsOfDate(year: Int, month: Int): LocalDate =
        LocalDate(year, month, lastDayOfMonth(year, month))
}
