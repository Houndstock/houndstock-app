package com.houndstock.app.ui.model

/**
 * Minimal placeholder model for the home screen.
 * Replace with a real domain model + repository once we wire the data layer.
 */
data class Investor(
    val id: String,
    val name: String,
    val firm: String,
    val holdingCount: Int,
    val topTickers: List<String>
)

val sampleInvestors = listOf(
    Investor("brk", "Warren Buffett", "Berkshire Hathaway", 41, listOf("AAPL", "BAC", "KO", "AXP")),
    Investor("scion", "Michael Burry", "Scion Asset Management", 13, listOf("BABA", "JD", "BIDU")),
    Investor("pershing", "Bill Ackman", "Pershing Square", 9, listOf("CMG", "HLT", "QSR", "GOOGL")),
    Investor("appaloosa", "David Tepper", "Appaloosa Management", 38, listOf("META", "AMZN", "MSFT", "NVDA")),
    Investor("baupost", "Seth Klarman", "Baupost Group", 27, listOf("LBRDK", "WSC", "GFL"))
)
