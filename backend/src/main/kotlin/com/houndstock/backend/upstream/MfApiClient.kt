package com.houndstock.backend.upstream

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.Closeable

/** Thin wrapper around mfapi.in (https://api.mfapi.in). */
class MfApiClient(
    private val baseUrl: String,
    private val client: HttpClient,
) : Closeable {

    suspend fun search(query: String): List<MfApiSearchHit> {
        val url = URLBuilder().takeFrom("$baseUrl/mf/search").apply {
            parameters.append("q", query)
        }.build()
        return client.get(url).body()
    }

    suspend fun latest(schemeCode: Long): MfApiSchemeResponse =
        client.get("$baseUrl/mf/$schemeCode/latest").body()

    suspend fun history(
        schemeCode: Long,
        startDate: String? = null,
        endDate: String? = null,
    ): MfApiSchemeResponse {
        val url = URLBuilder().takeFrom("$baseUrl/mf/$schemeCode").apply {
            if (startDate != null) parameters.append("startDate", startDate)
            if (endDate != null) parameters.append("endDate", endDate)
        }.build()
        return client.get(url).body()
    }

    override fun close() {
        client.close()
    }

    companion object {
        fun create(baseUrl: String = "https://api.mfapi.in"): MfApiClient {
            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 5_000
                }
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 2)
                    exponentialDelay()
                }
                defaultRequest {
                    headers.append("User-Agent", "houndstock-backend/0.1")
                }
            }
            return MfApiClient(baseUrl, client)
        }
    }
}
