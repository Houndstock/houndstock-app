package com.houndstock.backend.scraper.ppfas

import com.houndstock.backend.scraper.model.SchemePortfolio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/** Result of a single scrape attempt. */
data class ScrapeResult(
    val sourceUrl: String,
    val fileSha256: String,
    val portfolios: List<SchemePortfolio>,
)

/**
 * Downloads PPFAS's monthly portfolio XLSX and runs it through [PpfasParser].
 * Tries .xls then .xlsx (PPFAS alternates) — either way it's XLSX inside.
 */
class PpfasScraper(
    private val httpClient: HttpClient,
    private val parser: PpfasParser = PpfasParser(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun fetchMonth(year: Int, month: Int): ScrapeResult {
        val urls = PpfasUrlBuilder.candidateUrls(year, month)
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val bytes = downloadBytes(url) ?: continue
                val portfolios = parser.parse(bytes)
                return ScrapeResult(
                    sourceUrl = url,
                    fileSha256 = sha256Hex(bytes),
                    portfolios = portfolios,
                )
            } catch (t: Throwable) {
                log.warn("PPFAS fetch failed for $url: ${t.message}")
                lastError = t
            }
        }
        throw IllegalStateException(
            "Could not fetch PPFAS portfolio for $year-$month after trying ${urls.size} URLs",
            lastError,
        )
    }

    private suspend fun downloadBytes(url: String): ByteArray? {
        val resp: HttpResponse = httpClient.get(url)
        return when {
            resp.status == HttpStatusCode.NotFound -> {
                log.info("PPFAS file not found at $url (404)")
                null
            }
            !resp.status.isSuccess() -> throw ResponseException(resp, "PPFAS download failed: ${resp.status}")
            else -> resp.body()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
