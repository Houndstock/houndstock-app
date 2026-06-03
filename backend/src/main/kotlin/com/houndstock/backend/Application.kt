package com.houndstock.backend

import com.houndstock.backend.plugins.configureMonitoring
import com.houndstock.backend.plugins.configureRouting
import com.houndstock.backend.plugins.configureSerialization
import com.houndstock.backend.upstream.MfApiClient
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

/**
 * Application module. Wires plugins and routes. Kept top-level so
 * [io.ktor.server.testing.testApplication] in tests can reuse it.
 */
fun Application.module() {
    val mfApi = MfApiClient.create()
    monitor.subscribe(ApplicationStopping) {
        mfApi.close()
    }

    configureSerialization()
    configureMonitoring()
    configureRouting(mfApi)
}
