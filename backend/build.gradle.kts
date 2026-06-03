plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.houndstock"
version = "0.1.0"

application {
    mainClass.set("com.houndstock.backend.ApplicationKt")
    // Quiet Ktor's "development mode" banner unless DEVELOPMENT_MODE is set.
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=${System.getenv("DEVELOPMENT_MODE") ?: "false"}"
    )
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Ktor client (for upstream calls to mfapi.in)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Logging
    implementation(libs.logback.classic)

    // Test
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter)
    // Gradle 8.8+ needs the JUnit Platform launcher on the test runtime
    // classpath explicitly; without it, "Failed to load JUnit Platform".
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
