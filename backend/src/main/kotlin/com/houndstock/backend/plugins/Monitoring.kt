package com.houndstock.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(DefaultHeaders) {
        header("X-Houndstock-Backend", "0.1.0")
    }
    install(CallLogging) {
        level = Level.INFO
        // Skip the health endpoint to keep logs quiet — Fly.io polls it constantly.
        filter { call -> !call.request.path().startsWith("/health") }
    }
}
