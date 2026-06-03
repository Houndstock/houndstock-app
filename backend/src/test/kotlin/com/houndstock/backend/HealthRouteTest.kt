package com.houndstock.backend

import com.houndstock.backend.routes.HealthResponse
import com.houndstock.backend.routes.healthRoute
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthRouteTest {

    @Test
    fun `health returns ok and version`() = testApplication {
        application {
            install(ServerContentNegotiation) { json() }
            routing { healthRoute() }
        }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HealthResponse>()
        assertEquals("ok", body.status)
        assertTrue(body.version.isNotBlank(), "version should be populated")
        assertTrue(body.uptimeSeconds >= 0, "uptime should be non-negative")
    }
}
