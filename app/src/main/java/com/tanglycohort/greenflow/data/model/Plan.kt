package com.tanglycohort.greenflow.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Plan(
    val id: String? = null,
    val name: String? = null,
    val price: Double? = null,
    @SerialName("duration_days") val durationDays: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)
