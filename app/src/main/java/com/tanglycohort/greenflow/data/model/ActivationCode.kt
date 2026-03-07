package com.tanglycohort.greenflow.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivationCode(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val code: String? = null,
    val status: String? = null, // unused, used
    @SerialName("used_at") val usedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
