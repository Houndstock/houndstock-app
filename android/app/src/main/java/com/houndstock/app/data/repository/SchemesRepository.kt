package com.houndstock.app.data.repository

import com.houndstock.app.data.network.SchemesApi
import com.houndstock.app.data.network.dto.SchemeDetailDto
import com.houndstock.app.data.network.dto.SchemeSummaryDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the Schemes API. Returns Result<T> so callers can
 * surface failures uniformly. Add caching here later (Room + flow) once
 * we want offline-first behavior.
 */
@Singleton
class SchemesRepository @Inject constructor(
    private val api: SchemesApi,
) {
    suspend fun search(query: String): Result<List<SchemeSummaryDto>> =
        runCatching { api.search(query) }

    suspend fun detail(schemeCode: Long): Result<SchemeDetailDto> =
        runCatching { api.detail(schemeCode) }
}
