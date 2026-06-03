package com.houndstock.backend.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val uptimeSeconds: Long,
)

private val startedAtMs: Long = System.currentTimeMillis()
private const val VERSION = "0.1.0"

fun Route.healthRoute() {
    get("/health") {
        val uptime = (System.currentTimeMillis() - startedAtMs) / 1000
        call.respond(HealthResponse(status = "ok", version = VERSION, uptimeSeconds = uptime))
    }
}
