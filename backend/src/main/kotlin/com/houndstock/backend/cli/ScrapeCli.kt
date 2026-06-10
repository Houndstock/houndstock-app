package com.houndstock.backend.cli

import com.houndstock.backend.data.Database
import com.houndstock.backend.data.HoldingsRepository
import com.houndstock.backend.scraper.ppfas.PpfasScraper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ScrapeCli")

/**
 * Entry point for `gradle run --args="scrape ppfas <year> <month>"`.
 *
 * Args (after the leading "scrape" is stripped):
 *   amc-slug year month
 *
 * If DATABASE_URL is set, persists results. Otherwise just prints stats
 * (handy for verifying parsing without a Postgres).
 */
suspend fun runScrapeCli(args: List<String>): Int {
    if (args.size != 3) {
        System.err.println("usage: scrape <amc> <year> <month>   e.g. scrape ppfas 2026 4")
        return 2
    }
    val amc = args[0]
    val year = args[1].toIntOrNull() ?: return die("year must be an integer, got '${args[1]}'")
    val month = args[2].toIntOrNull() ?: return die("month must be an integer, got '${args[2]}'")
    if (month !in 1..12) return die("month must be 1..12, got $month")

    return when (amc) {
        "ppfas" -> runPpfas(year, month)
        else -> die("unknown AMC slug '$amc'. Supported: ppfas")
    }
}

private suspend fun runPpfas(year: Int, month: Int): Int {
    val client = buildHttpClient()
    val scraper = PpfasScraper(client)
    val result = try {
        scraper.fetchMonth(year, month)
    } finally {
        client.close()
    }

    println("PPFAS $year-$month  source=${result.sourceUrl}  sha256=${result.fileSha256.take(12)}…")
    println("  ${result.portfolios.size} schemes, " +
        "${result.portfolios.sumOf { it.holdings.size }} holdings total")
    for (p in result.portfolios) {
        println("    ${p.sheetCode}  ${p.schemeName}  (${p.holdings.size} holdings)")
    }

    if (Database.initFromEnv()) {
        val saved = HoldingsRepository().saveScrape(
            amcSlug = "ppfas",
            amcName = "PPFAS Mutual Fund",
            portfolios = result.portfolios,
            sourceUrl = result.sourceUrl,
            fileSha256 = result.fileSha256,
        )
        println("✓ Persisted $saved holdings to Postgres")
    } else {
        println("(DATABASE_URL not set — not persisting. Set it to write to Postgres.)")
    }
    return 0
}

private fun buildHttpClient() = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
    }
    defaultRequest {
        headers.append("User-Agent", "houndstock-backend/0.1")
    }
}

private fun die(msg: String): Int {
    log.error(msg)
    System.err.println(msg)
    return 2
}
