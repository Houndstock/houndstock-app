package com.houndstock.backend.data

import com.houndstock.backend.scraper.model.HoldingSection
import com.houndstock.backend.scraper.model.ParsedHolding
import com.houndstock.backend.scraper.model.SchemePortfolio
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

/**
 * Writes a scraper result into the DB. Everything in one transaction.
 *
 *  1. Ensure AMC row exists
 *  2. Upsert every distinct stock (insert if new, update name/industry if changed)
 *  3. Ensure scheme row exists
 *  4. Wipe-and-replace holdings for (scheme, as_of_date)
 *  5. Upsert portfolio_publishes audit row
 */
class HoldingsRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun saveScrape(
        amcSlug: String,
        amcName: String,
        portfolios: List<SchemePortfolio>,
        sourceUrl: String,
        fileSha256: String,
    ): Int = transaction {
        val now = Clock.System.now()
        val amcId = ensureAmc(amcSlug, amcName, now)

        // Distinct stocks across all portfolios
        portfolios.flatMap { it.holdings }
            .distinctBy { it.isin }
            .forEach { upsertStock(it, now) }

        var totalHoldings = 0
        for (portfolio in portfolios) {
            val schemeId = ensureScheme(amcId, portfolio.schemeName, portfolio.sheetCode, now)
            replaceHoldings(schemeId, portfolio)
            totalHoldings += portfolio.holdings.size

            recordPublish(
                amcId = amcId,
                asOfDate = portfolio.asOfDate,
                sourceUrl = sourceUrl,
                fileSha256 = fileSha256,
                holdingsCount = portfolio.holdings.size,
                fetchedAt = now,
            )
        }
        log.info("Persisted ${portfolios.size} schemes / $totalHoldings holdings for $amcSlug")
        totalHoldings
    }

    private fun ensureAmc(slug: String, name: String, now: Instant): Int {
        AmcsTable.insertIgnore {
            it[AmcsTable.slug] = slug
            it[AmcsTable.name] = name
            it[createdAt] = now
        }
        return AmcsTable.selectAll().where { AmcsTable.slug eq slug }
            .single()[AmcsTable.id].value
    }

    /**
     * Insert if new, otherwise update name + industry (cheapest correct option:
     * pull current row, compare, conditionally update — but we don't need that
     * optimization yet). Always touches updated_at.
     */
    private fun upsertStock(h: ParsedHolding, now: Instant) {
        val inserted = StocksTable.insertIgnore {
            it[isin] = h.isin
            it[name] = h.instrumentName
            it[industry] = h.industry
            it[createdAt] = now
            it[updatedAt] = now
        }.insertedCount
        if (inserted == 0) {
            StocksTable.update({ StocksTable.isin eq h.isin }) {
                it[name] = h.instrumentName
                it[industry] = h.industry
                it[updatedAt] = now
            }
        }
    }

    private fun ensureScheme(
        amcId: Int,
        name: String,
        shortCode: String?,
        now: Instant,
    ): Int {
        SchemesTable.insertIgnore {
            it[SchemesTable.amcId] = amcId
            it[SchemesTable.name] = name
            it[SchemesTable.shortCode] = shortCode
            it[createdAt] = now
            it[updatedAt] = now
        }
        return SchemesTable.selectAll()
            .where { (SchemesTable.amcId eq amcId) and (SchemesTable.name eq name) }
            .single()[SchemesTable.id].value
    }

    private fun replaceHoldings(schemeId: Int, portfolio: SchemePortfolio) {
        HoldingsTable.deleteWhere {
            (HoldingsTable.schemeId eq schemeId) and
                (HoldingsTable.asOfDate eq portfolio.asOfDate)
        }
        for (h in portfolio.holdings) {
            HoldingsTable.insert {
                it[HoldingsTable.schemeId] = schemeId
                it[stockIsin] = h.isin
                it[asOfDate] = portfolio.asOfDate
                // Store as percentage (0..100). Source has it as 0..1 fraction.
                it[weightPct] = h.weightFraction.movePointRight(2)
                it[marketValueLakhs] = h.marketValueLakhs
                it[quantity] = h.quantity
                it[section] = h.section.toSqlValue()
            }
        }
    }

    private fun recordPublish(
        amcId: Int,
        asOfDate: LocalDate,
        sourceUrl: String,
        fileSha256: String,
        holdingsCount: Int,
        fetchedAt: Instant,
    ) {
        val existing = PortfolioPublishesTable.selectAll().where {
            (PortfolioPublishesTable.amcId eq amcId) and
                (PortfolioPublishesTable.asOfDate eq asOfDate)
        }.firstOrNull()

        if (existing == null) {
            PortfolioPublishesTable.insert {
                it[PortfolioPublishesTable.amcId] = amcId
                it[PortfolioPublishesTable.asOfDate] = asOfDate
                it[PortfolioPublishesTable.sourceUrl] = sourceUrl
                it[PortfolioPublishesTable.fileSha256] = fileSha256
                it[PortfolioPublishesTable.holdingsCount] = holdingsCount
                it[PortfolioPublishesTable.fetchedAt] = fetchedAt
            }
        } else {
            PortfolioPublishesTable.update({
                (PortfolioPublishesTable.amcId eq amcId) and
                    (PortfolioPublishesTable.asOfDate eq asOfDate)
            }) {
                it[PortfolioPublishesTable.sourceUrl] = sourceUrl
                it[PortfolioPublishesTable.fileSha256] = fileSha256
                it[PortfolioPublishesTable.holdingsCount] = holdingsCount
                it[PortfolioPublishesTable.fetchedAt] = fetchedAt
            }
        }
    }
}

private fun HoldingSection.toSqlValue(): String = when (this) {
    HoldingSection.EQUITY_LISTED -> "equity_listed"
    HoldingSection.EQUITY_UNLISTED -> "equity_unlisted"
    HoldingSection.EQUITY_FOREIGN -> "equity_foreign"
    HoldingSection.DEBT_LISTED -> "debt_listed"
    HoldingSection.DEBT_UNLISTED -> "debt_unlisted"
    HoldingSection.DEBT_MONEY_MARKET -> "debt_money_market"
    HoldingSection.DERIVATIVES -> "derivatives"
    HoldingSection.CASH_AND_EQUIVALENTS -> "cash_and_equivalents"
    HoldingSection.REITS_INVITS -> "reits_invits"
    HoldingSection.OTHER -> "other"
}
