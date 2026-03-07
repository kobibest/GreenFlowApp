package com.tanglycohort.greenflow.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("plan_id") val planId: String? = null,
    val status: String? = null, // active, trial, blocked, cancelled
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
