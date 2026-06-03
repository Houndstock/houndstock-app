package com.houndstock.backend.routes

import com.houndstock.backend.plugins.ErrorBody
import com.houndstock.backend.upstream.MfApiClient
import com.houndstock.backend.upstream.MfApiSearchHit
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class SchemeSummary(
    val schemeCode: Long,
    val schemeName: String,
)

@Serializable
data class SchemeDetail(
    val schemeCode: Long,
    val schemeName: String,
    val fundHouse: String,
    val schemeType: String,
    val schemeCategory: String,
    val isinGrowth: String?,
    val latestNavDate: String?,
    val latestNav: String?,
)

fun Route.schemesRoute(mfApi: MfApiClient) {
    route("/schemes") {

        // GET /schemes/search?q=parag
        get("/search") {
            val q = call.request.queryParameters["q"]?.trim().orEmpty()
            if (q.length < 2) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorBody("invalid_query", "Pass ?q= with at least 2 chars")
                )
                return@get
            }
            val hits = mfApi.search(q).map { it.toSummary() }
            call.respond(hits)
        }

        // GET /schemes/{code}
        get("/{code}") {
            val code = call.parameters["code"]?.toLongOrNull()
            if (code == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorBody("invalid_code", "scheme code must be a positive integer")
                )
                return@get
            }
            val resp = mfApi.latest(code)
            val latest = resp.data.firstOrNull()
            call.respond(
                SchemeDetail(
                    schemeCode = resp.meta.scheme_code,
                    schemeName = resp.meta.scheme_name,
                    fundHouse = resp.meta.fund_house,
                    schemeType = resp.meta.scheme_type,
                    schemeCategory = resp.meta.scheme_category,
                    isinGrowth = resp.meta.isin_growth,
                    latestNavDate = latest?.date,
                    latestNav = latest?.nav,
                )
            )
        }

        // GET /schemes/{code}/holdings — not yet implemented.
        // mfdata.in is the planned source but is currently unreachable;
        // returning 503 + Retry-After is more honest than a fake 200.
        get("/{code}/holdings") {
            call.response.header(HttpHeaders.RetryAfter, "86400") // one day
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorBody(
                    "holdings_unavailable",
                    "Portfolio holdings source is not wired up yet. Tracked in backend roadmap."
                )
            )
        }
    }
}

private fun MfApiSearchHit.toSummary() = SchemeSummary(schemeCode, schemeName)
