package com.tanglycohort.greenflow.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("whatsapp_group_id") val whatsappGroupId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
