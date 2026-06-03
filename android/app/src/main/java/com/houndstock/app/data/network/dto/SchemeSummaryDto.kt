package com.houndstock.app.data.network.dto

import kotlinx.serialization.Serializable

/** Mirrors backend's SchemeSummary. */
@Serializable
data class SchemeSummaryDto(
    val schemeCode: Long,
    val schemeName: String,
)
