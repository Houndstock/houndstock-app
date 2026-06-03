package com.houndstock.backend.upstream

import kotlinx.serialization.Serializable

/** mfapi.in scheme search hit. Example:
 *   {"schemeCode": 122639, "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth"}
 */
@Serializable
data class MfApiSearchHit(
    val schemeCode: Long,
    val schemeName: String,
)

/** mfapi.in scheme detail. Single `data` entry on /{code}/latest, full history on /{code}. */
@Serializable
data class MfApiSchemeResponse(
    val meta: MfApiSchemeMeta,
    val data: List<MfApiNavPoint>,
    val status: String,
)

@Serializable
data class MfApiSchemeMeta(
    val fund_house: String,
    val scheme_type: String,
    val scheme_category: String,
    val scheme_code: Long,
    val scheme_name: String,
    val isin_growth: String? = null,
    val isin_div_reinvestment: String? = null,
)

@Serializable
data class MfApiNavPoint(
    /** dd-MM-yyyy. We pass through as a string for now; convert to LocalDate when needed. */
    val date: String,
    /** Stringified decimal in mfapi.in's response. */
    val nav: String,
)
