package com.houndstock.backend.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** AMCs (e.g. PPFAS, HDFC AMC). */
object AmcsTable : IntIdTable("amcs") {
    val slug = varchar("slug", 64).uniqueIndex()
    val name = varchar("name", 128).uniqueIndex()
    val createdAt = timestamp("created_at")
}

/** Stocks keyed by ISIN. */
object StocksTable : Table("stocks") {
    val isin = varchar("isin", 12)
    val name = varchar("name", 255)
    val industry = varchar("industry", 128).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(isin)
}

/** Mutual fund schemes. */
object SchemesTable : IntIdTable("schemes") {
    val amcId = reference("amc_id", AmcsTable)
    val name = varchar("name", 255)
    val shortCode = varchar("short_code", 32).nullable()
    val schemeType = varchar("scheme_type", 64).nullable()
    val schemeCategory = varchar("scheme_category", 128).nullable()
    val mfapiSchemeCode = long("mfapi_scheme_code").nullable().uniqueIndex()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    init {
        uniqueIndex(amcId, name)
    }
}

/** Point-in-time holdings facts. */
object HoldingsTable : Table("holdings") {
    val schemeId = reference("scheme_id", SchemesTable)
    val stockIsin = reference("stock_isin", StocksTable.isin)
    val asOfDate = date("as_of_date")
    val weightPct = decimal("weight_pct", 10, 6)
    val marketValueLakhs = decimal("market_value_lakhs", 18, 2).nullable()
    val quantity = long("quantity").nullable()

    /** Stored as enum in Postgres but we read/write as text via Exposed. */
    val section = varchar("section", 32)

    override val primaryKey = PrimaryKey(schemeId, stockIsin, asOfDate)
}

/** Audit trail: which file did we ingest, for which (amc, date)? */
object PortfolioPublishesTable : IntIdTable("portfolio_publishes") {
    val amcId = reference("amc_id", AmcsTable)
    val asOfDate = date("as_of_date")
    val sourceUrl = text("source_url")
    val fileSha256 = varchar("file_sha256", 64).nullable()
    val fetchedAt = timestamp("fetched_at")
    val holdingsCount = integer("holdings_count").nullable()

    init {
        uniqueIndex(amcId, asOfDate)
    }
}
