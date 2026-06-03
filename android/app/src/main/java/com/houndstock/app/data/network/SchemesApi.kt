package com.houndstock.app.data.network

import com.houndstock.app.data.network.dto.SchemeDetailDto
import com.houndstock.app.data.network.dto.SchemeSummaryDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit interface for the Houndstock backend's /schemes routes. */
interface SchemesApi {

    @GET("schemes/search")
    suspend fun search(@Query("q") query: String): List<SchemeSummaryDto>

    @GET("schemes/{code}")
    suspend fun detail(@Path("code") schemeCode: Long): SchemeDetailDto
}
