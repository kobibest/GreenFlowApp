package com.tanglycohort.greenflow.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.Purchase
import com.tanglycohort.greenflow.data.model.Plan
import com.tanglycohort.greenflow.data.model.Profile
import com.tanglycohort.greenflow.data.model.Subscription
import com.tanglycohort.greenflow.data.repository.AuthRepository
import com.tanglycohort.greenflow.data.repository.PlansRepository
import com.tanglycohort.greenflow.data.repository.ProfilesRepository
import com.tanglycohort.greenflow.data.repository.SubscriptionsRepository
import com.tanglycohort.greenflow.util.PhoneUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

enum class SubscriptionDisplayStatus {
    INACTIVE, BLOCKED, CANCELLED, CANCELLED_VALID, GRACE_PERIOD, TRIAL_EXPIRED, EXPIRED, TRIAL, ACTIVE
}

data class DashboardState(
    val loading: Boolean = true,
    val subscription: Subscription? = null,
    val hasEverHadSubscription: Boolean = false,
    val plans: List<Plan> = emptyList(),
    val profile: Profile? = null,
    val profileSaveSuccess: Boolean = false,
    val error: String? = null
)

sealed class DashboardEvent {
    data object NavigateToLogin : DashboardEvent()
    data class ShowError(val message: String) : DashboardEvent()
    data object TrialStarted : DashboardEvent()
}

class DashboardViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val subscriptionsRepository: SubscriptionsRepository = SubscriptionsRepository(),
    private val plansRepository: PlansRepository = PlansRepository(),
    private val profilesRepository: ProfilesRepository = ProfilesRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DashboardEvent>()
    val events = _events.asSharedFlow()

    /**
     * @param querySubscriptions If non-null, after loading from Supabase this is called to get Google Play purchases; any purchase not already in Supabase is synced via verify-play-purchase.
     * @param accessToken Required when querySubscriptions is non-null, for verify-play-purchase.
     * @param packageName Required when querySubscriptions is non-null, for verify-play-purchase.
     */
    fun loadDashboard(
        userId: String,
        querySubscriptions: ((callback: (List<Purchase>) -> Unit) -> Unit)? = null,
        accessToken: String? = null,
        packageName: String? = null
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val subRepo = subscriptionsRepository
            val profRepo = profilesRepository
            val plansRepo = plansRepository
            val results = kotlinx.coroutines.coroutineScope {
                awaitAll(
                    async { subRepo.getActiveSubscription(userId) },
                    async { subRepo.hasEverHadSubscription(userId) },
                    async { profRepo.getProfile(userId) },
                    async { plansRepo.getPlans() }
                )
            }
            val sub = results[0] as? Subscription
            val hasEver = results[1] as? Boolean ?: false
            val profile = results[2] as? Profile
            val plans = (results[3] as? List<Plan>) ?: emptyList()
            _state.value = _state.value.copy(
                loading = false,
                subscription = sub,
                hasEverHadSubscription = hasEver,
                profile = profile,
                plans = plans
            )
            if (querySubscriptions != null && !accessToken.isNullOrBlank() && !packageName.isNullOrBlank()) {
                querySubscriptions.invoke { purchases ->
                    viewModelScope.launch {
                        syncUnsyncedPurchases(userId, purchases, accessToken, packageName)
                    }
                }
            }
        }
    }

    /**
     * For each purchase not already reflected in Supabase subscription state, call verify-play-purchase to sync.
     * Does not override Supabase status with Google result; Google is used only to detect unsynced purchases.
     */
    private suspend fun syncUnsyncedPurchases(
        userId: String,
        purchases: List<Purchase>,
        accessToken: String,
        packageName: String
    ) {
        val state = _state.value
        val plans = state.plans
        val currentPlanId = state.subscription?.planId
        for (purchase in purchases) {
            val productId = purchase.products.firstOrNull() ?: continue
            val plan = plans.find { it.playProductId == productId } ?: continue
            if (currentPlanId == plan.id) continue
            subscriptionsRepository.verifyPlayPurchase(
                accessToken = accessToken,
                purchaseToken = purchase.purchaseToken,
                productId = productId,
                packageName = packageName
            ).onSuccess {
                loadDashboard(userId, null, null, null)
            }
        }
    }

    fun subscriptionDisplayStatus(): SubscriptionDisplayStatus {
        val sub = _state.value.subscription ?: return SubscriptionDisplayStatus.INACTIVE
        val endsAtInstant = sub.endsAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val now = Instant.now()
        val isEnded = endsAtInstant != null && endsAtInstant <= now
        return when {
            sub.status == "blocked" -> SubscriptionDisplayStatus.BLOCKED
            sub.status == "cancelled" && !isEnded -> SubscriptionDisplayStatus.CANCELLED_VALID
            sub.status == "cancelled" && isEnded -> SubscriptionDisplayStatus.EXPIRED
            sub.paymentState == "pending" && endsAtInstant != null && endsAtInstant > now -> SubscriptionDisplayStatus.GRACE_PERIOD
            sub.status == "trial" && isEnded -> SubscriptionDisplayStatus.TRIAL_EXPIRED
            isEnded -> SubscriptionDisplayStatus.EXPIRED
            sub.status == "trial" -> SubscriptionDisplayStatus.TRIAL
            else -> SubscriptionDisplayStatus.ACTIVE
        }
    }

    /** True if the user has full access (active, trial, or cancelled-but-still-valid). */
    fun hasFullAccess(): Boolean {
        val status = subscriptionDisplayStatus()
        return status == SubscriptionDisplayStatus.ACTIVE ||
            status == SubscriptionDisplayStatus.TRIAL ||
            status == SubscriptionDisplayStatus.CANCELLED_VALID ||
            status == SubscriptionDisplayStatus.GRACE_PERIOD
    }

    fun canShowTrialButton(): Boolean =
        subscriptionDisplayStatus() == SubscriptionDisplayStatus.INACTIVE && !_state.value.hasEverHadSubscription

    fun startTrial(userId: String) {
        viewModelScope.launch {
            subscriptionsRepository.insertTrial(userId).onSuccess {
                loadDashboard(userId)
                _events.emit(DashboardEvent.TrialStarted)
            }.onFailure { e ->
                _events.emit(DashboardEvent.ShowError(e.message ?: "שגיאה"))
            }
        }
    }

    fun subscribeToPlan(userId: String, plan: Plan) {
        viewModelScope.launch {
            val planId = plan.id ?: return@launch
            subscriptionsRepository.subscribeToPlan(userId, planId, plan.durationDays).onSuccess {
                loadDashboard(userId)
            }.onFailure { e ->
                _events.emit(DashboardEvent.ShowError(e.message ?: "שגיאה בהפעלת מנוי"))
            }
        }
    }

    /** Call after a successful Google Play purchase: verify with backend, then refresh dashboard. */
    fun onPlayPurchaseSuccess(
        userId: String,
        accessToken: String,
        purchaseToken: String,
        productId: String,
        packageName: String
    ) {
        viewModelScope.launch {
            subscriptionsRepository.verifyPlayPurchase(
                accessToken = accessToken,
                purchaseToken = purchaseToken,
                productId = productId,
                packageName = packageName
            ).onSuccess {
                loadDashboard(userId)
            }.onFailure { e ->
                _events.emit(DashboardEvent.ShowError(e.message ?: "שגיאה באימות התשלום"))
            }
        }
    }

    fun saveProfile(userId: String, name: String?, phone: String?) {
        viewModelScope.launch {
            val normalizedPhone = phone?.let { PhoneUtils.normalizeAndValidate(it) }
            if (phone != null && normalizedPhone == null) {
                _events.emit(DashboardEvent.ShowError("מספר טלפון לא תקין"))
                return@launch
            }
            profilesRepository.updateProfile(userId, name?.takeIf { it.isNotBlank() }, normalizedPhone)
                .onSuccess {
                    _state.value = _state.value.copy(profileSaveSuccess = true, profile = _state.value.profile?.copy(name = name ?: _state.value.profile?.name, phone = normalizedPhone ?: _state.value.profile?.phone))
                }
                .onFailure { e ->
                    _events.emit(DashboardEvent.ShowError(e.message ?: "שגיאה בשמירה"))
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            _events.emit(DashboardEvent.NavigateToLogin)
        }
    }
}
