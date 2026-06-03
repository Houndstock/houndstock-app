package com.houndstock.app.data.network.dto

import kotlinx.serialization.Serializable

/** Mirrors backend's SchemeDetail. Used by the scheme detail screen (not yet built). */
@Serializable
data class SchemeDetailDto(
    val schemeCode: Long,
    val schemeName: String,
    val fundHouse: String,
    val schemeType: String,
    val schemeCategory: String,
    val isinGrowth: String? = null,
    val latestNavDate: String? = null,
    val latestNav: String? = null,
)
