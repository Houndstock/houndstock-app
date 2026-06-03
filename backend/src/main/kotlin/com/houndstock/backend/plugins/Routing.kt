package com.houndstock.backend.plugins

import com.houndstock.backend.routes.healthRoute
import com.houndstock.backend.routes.schemesRoute
import com.houndstock.backend.upstream.MfApiClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class ErrorBody(val error: String, val detail: String? = null)

fun Application.configureRouting(mfApi: MfApiClient) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorBody("internal_error", cause.message)
            )
        }
    }
    routing {
        healthRoute()
        schemesRoute(mfApi)
    }
}
