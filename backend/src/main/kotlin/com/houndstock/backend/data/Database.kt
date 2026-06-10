package com.houndstock.backend.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Thin wrapper around HikariCP + Flyway + Exposed.
 *
 * - Reads connection from DATABASE_URL (jdbc URL or libpq style).
 * - Runs Flyway migrations on startup.
 * - Hands the Hikari DataSource to Exposed.
 *
 * Init is intentionally optional — if DATABASE_URL isn't set we skip everything
 * and let callers (server, scrape job) fail fast when they actually need the DB.
 */
object Database {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var dataSource: HikariDataSource? = null

    @Volatile
    private var exposed: ExposedDatabase? = null

    fun isInitialized(): Boolean = dataSource != null

    fun datasource(): DataSource = requireNotNull(dataSource) {
        "Database.init() must be called first (or DATABASE_URL must be set)."
    }

    /** Initialize from env. Returns true if a connection was established. */
    fun initFromEnv(): Boolean {
        if (dataSource != null) return true
        val raw = System.getenv("DATABASE_URL") ?: return false
        val jdbcUrl = toJdbcUrl(raw)
        log.info("Initializing database connection (host masked).")
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = (System.getenv("DB_POOL_SIZE")?.toIntOrNull() ?: 5)
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            poolName = "houndstock-pool"
        }
        val ds = HikariDataSource(config)
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        exposed = ExposedDatabase.connect(ds)
        dataSource = ds
        return true
    }

    fun close() {
        dataSource?.close()
        dataSource = null
        exposed = null
    }

    /**
     * Accept either a `postgres://user:pass@host:port/db` URL (libpq-style,
     * common in Fly.io / Heroku env) or a `jdbc:postgresql://...` URL.
     */
    private fun toJdbcUrl(raw: String): String {
        if (raw.startsWith("jdbc:")) return raw
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) {
            error("DATABASE_URL must start with jdbc:postgresql://, postgres://, or postgresql://")
        }
        val withoutScheme = raw.substringAfter("://")
        val (userInfo, hostAndPath) = withoutScheme.split("@", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else "" to withoutScheme
        }
        val (user, pass) = userInfo.split(":", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else it[0] to ""
        }
        val pathSep = hostAndPath.indexOf('/').takeIf { it >= 0 } ?: hostAndPath.length
        val host = hostAndPath.substring(0, pathSep)
        val pathAndQuery = hostAndPath.substring(pathSep)
        val builder = StringBuilder("jdbc:postgresql://").append(host).append(pathAndQuery)
        val params = mutableListOf<String>()
        if (user.isNotEmpty()) params += "user=$user"
        if (pass.isNotEmpty()) params += "password=$pass"
        if (params.isNotEmpty()) {
            builder.append(if (pathAndQuery.contains('?')) "&" else "?")
            builder.append(params.joinToString("&"))
        }
        return builder.toString()
    }
}
