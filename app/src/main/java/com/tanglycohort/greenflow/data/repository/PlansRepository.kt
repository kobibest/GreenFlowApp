package com.tanglycohort.greenflow.data.repository

import com.tanglycohort.greenflow.data.model.Plan
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlansRepository {

    private val client get() = SupabaseProvider.client

    suspend fun getPlans(): List<Plan> = withContext(Dispatchers.IO) {
        runCatching {
            client.from("plans").select().decodeList<Plan>()
        }.getOrElse { emptyList() }
    }
}
