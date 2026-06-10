package com.houndstock.backend

import com.houndstock.backend.cli.runScrapeCli
import com.houndstock.backend.data.Database
import com.houndstock.backend.plugins.configureMonitoring
import com.houndstock.backend.plugins.configureRouting
import com.houndstock.backend.plugins.configureSerialization
import com.houndstock.backend.upstream.MfApiClient
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "scrape") {
        val code = runBlocking { runScrapeCli(args.drop(1)) }
        exitProcess(code)
    }
    serve()
}

private fun serve() {
    // Initialize DB if configured. The server can still start without one;
    // the routes that need DB will fail at request time with a clear error.
    if (Database.initFromEnv()) {
        println("✓ Database connection established (Flyway migrations applied).")
    } else {
        println("⚠ DATABASE_URL not set — running server without persistence.")
    }
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
        Database.close()
    }

    configureSerialization()
    configureMonitoring()
    configureRouting(mfApi)
}
