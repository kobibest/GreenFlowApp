package com.tanglycohort.greenflow.data.repository

import com.tanglycohort.greenflow.data.model.Profile
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfilesRepository {

    private val client get() = SupabaseProvider.client

    /** profiles.id is the auth user id (PK). */
    suspend fun getProfile(userId: String): Profile? = withContext(Dispatchers.IO) {
        runCatching {
            client.from("profiles").select {
                filter { eq("id", userId) }
                limit(1)
            }.decodeList<Profile>().firstOrNull()
        }.getOrNull()
    }

    suspend fun updateProfile(userId: String, name: String?, phone: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updates = mutableMapOf<String, Any?>()
            if (name != null) updates["name"] = name
            if (phone != null) updates["phone"] = phone
            if (updates.isEmpty()) return@runCatching
            client.from("profiles").update(updates) {
                filter { eq("id", userId) }
            }
        }.map { }
    }

    suspend fun setDeviceId(userId: String, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.from("profiles").update(mapOf("device_id" to deviceId)) {
                filter { eq("id", userId) }
            }
        }.map { }
    }

    suspend fun clearDeviceId(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.from("profiles").update(mapOf("device_id" to null)) {
                filter { eq("id", userId) }
            }
        }.map { }
    }
}
