package com.tanglycohort.greenflow.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.WorkManager
import com.google.android.material.card.MaterialCardView
import com.tanglycohort.greenflow.AppLog
import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.DeviceId
import com.tanglycohort.greenflow.FetchFiltersWorker
import com.tanglycohort.greenflow.data.repository.ProfilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tanglycohort.greenflow.FilterSystemState
import com.tanglycohort.greenflow.R
import com.tanglycohort.greenflow.RuntimePermissions
import com.tanglycohort.greenflow.SmsFilter
import com.tanglycohort.greenflow.databinding.FragmentDashboardBinding
import com.tanglycohort.greenflow.debug.DebugAgentLog
import com.tanglycohort.greenflow.service.WebhookService
import com.tanglycohort.greenflow.supabase.SupabaseAuth
import io.github.jan.supabase.gotrue.auth
import java.time.Instant
import java.time.format.DateTimeFormatter

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // #region agent log
        DebugAgentLog.log("DashboardFragment.kt:onViewCreated", "Dashboard onViewCreated start", emptyMap(), "H4")
        // #endregion

        val userId = com.tanglycohort.greenflow.supabase.SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: com.tanglycohort.greenflow.data.repository.AuthRepository().getUserIdFromSession()
        if (userId == null) {
            findNavController().navigate(R.id.LoginFragment)
            return
        }

        WebhookService.setUserId(requireContext(), userId)
        if (WebhookService.getWebhookUrl(requireContext()) == null) {
            WebhookService.setWebhookUrl(requireContext(), BuildConfig.SERVER_BASE_URL + "/functions/v1/sms-webhook")
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ProfilesRepository().setDeviceId(userId, DeviceId.get(requireContext()))
        }

        // Auto-trigger filter fetch when dashboard is shown and no filters loaded yet (or to refresh)
        if (SmsFilter.getFilters(requireContext()).isNullOrEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val token = withContext(Dispatchers.IO) { SupabaseAuth.getValidAccessToken(forceRefresh = true) }
                if (token != null) FetchFiltersWorker.enqueueOnce(requireContext(), accessToken = token)
            }
        }

        viewModel.loadDashboard(userId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                binding.dashboardProgress.visibility = if (state.loading) View.VISIBLE else View.GONE
                if (!state.loading) {
                    val status = viewModel.subscriptionDisplayStatus()
                    val isActive = status == SubscriptionDisplayStatus.ACTIVE || status == SubscriptionDisplayStatus.TRIAL ||
                        status == SubscriptionDisplayStatus.CANCELLED_VALID || status == SubscriptionDisplayStatus.GRACE_PERIOD
                    val sub = state.subscription
                    binding.dashboardSubscriptionStatus.text = when (status) {
                        SubscriptionDisplayStatus.ACTIVE -> getString(R.string.status_active)
                        SubscriptionDisplayStatus.TRIAL -> {
                            val daysRemaining = sub?.endsAt?.let { end ->
                                runCatching {
                                    (java.time.Instant.parse(end).toEpochMilli() - System.currentTimeMillis()) / 86400000
                                }.getOrNull()?.toInt() ?: 0
                            } ?: 0
                            getString(R.string.subscription_trial_active, daysRemaining.coerceAtLeast(0))
                        }
                        SubscriptionDisplayStatus.CANCELLED_VALID -> sub?.endsAt?.let { getString(R.string.subscription_cancelled_valid, formatDate(it)) } ?: getString(R.string.status_active)
                        SubscriptionDisplayStatus.GRACE_PERIOD -> getString(R.string.subscription_grace_period)
                        SubscriptionDisplayStatus.BLOCKED -> getString(R.string.status_blocked)
                        SubscriptionDisplayStatus.EXPIRED, SubscriptionDisplayStatus.TRIAL_EXPIRED -> getString(R.string.status_expired)
                        SubscriptionDisplayStatus.CANCELLED -> "בוטל"
                        SubscriptionDisplayStatus.INACTIVE -> getString(R.string.status_inactive)
                    }
                    val plan = sub?.planId?.let { pid -> state.plans.find { it.id == pid } }
                    val durationDays = plan?.durationDays ?: 0
                    // Only monthly is offered; show type only for monthly
                    val typeStr = when {
                        durationDays <= 0 -> null
                        durationDays <= 35 -> getString(R.string.subscription_type_monthly)
                        else -> null // annual no longer offered
                    }
                    if (typeStr != null && isActive) {
                        binding.dashboardSubscriptionTypeLabel.visibility = View.VISIBLE
                        binding.dashboardSubscriptionType.visibility = View.VISIBLE
                        binding.dashboardSubscriptionType.text = typeStr
                    } else {
                        binding.dashboardSubscriptionTypeLabel.visibility = View.VISIBLE
                        binding.dashboardSubscriptionType.visibility = View.VISIBLE
                        binding.dashboardSubscriptionType.text = typeStr ?: "—"
                    }
                    val endDateStr = sub?.endsAt?.let { formatDate(it) } ?: "—"
                    binding.dashboardSubscriptionEndLabel.visibility = View.VISIBLE
                    binding.dashboardSubscriptionEndDate.visibility = View.VISIBLE
                    binding.dashboardSubscriptionEndDate.text = endDateStr
                    when (status) {
                        SubscriptionDisplayStatus.CANCELLED_VALID -> {
                            binding.btnManageSubscription.text = getString(R.string.btn_renew_subscription)
                            binding.btnManageSubscription.visibility = View.VISIBLE
                        }
                        SubscriptionDisplayStatus.GRACE_PERIOD -> {
                            binding.btnManageSubscription.text = getString(R.string.btn_manage_subscription)
                            binding.btnManageSubscription.visibility = View.VISIBLE
                        }
                        else -> {
                            binding.btnManageSubscription.text = getString(R.string.btn_manage_subscription)
                            binding.btnManageSubscription.visibility = if (isActive) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }

        binding.btnManageSubscription.setOnClickListener {
            val status = viewModel.subscriptionDisplayStatus()
            if (status == SubscriptionDisplayStatus.CANCELLED_VALID) {
                val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=${requireContext().packageName}")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } else {
                findNavController().navigate(R.id.action_DashboardFragment_to_SubscriptionFragment)
            }
        }

        // 1. Toggle On/Off
        binding.mainSwitch.isChecked = WebhookService.isServiceEnabled(requireContext())
        binding.mainSwitch.setOnCheckedChangeListener { _, isChecked ->
            WebhookService.setServiceEnabled(requireContext(), isChecked)
        }

        // 2. Refresh filters + last update + filters status
        updateLastFiltersUpdateText()
        updateFiltersStatusText()
        observeFiltersFetchWork()
        binding.refreshFiltersButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val token = withContext(Dispatchers.IO) { SupabaseAuth.getValidAccessToken(forceRefresh = true) }
                if (token == null) {
                    AppLog.e("DashboardFragment", "Session refresh failed before fetch filters")
                    Toast.makeText(requireContext(), R.string.main_refresh_session_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                FetchFiltersWorker.enqueueOnce(requireContext(), accessToken = token)
                Toast.makeText(requireContext(), R.string.main_refresh_filters_toast, Toast.LENGTH_SHORT).show()
                updateLastFiltersUpdateText()
                updateFiltersStatusText()
            }
        }

        // 3. Denied permissions list – build dynamically and open Permissions screen on click
        refreshDeniedPermissionsList()
    }

    override fun onResume() {
        super.onResume()
        val userId = com.tanglycohort.greenflow.supabase.SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: com.tanglycohort.greenflow.data.repository.AuthRepository().getUserIdFromSession()
        if (userId != null) viewModel.loadDashboard(userId)
        updateLastFiltersUpdateText()
        updateFiltersStatusText()
        refreshDeniedPermissionsList()
    }

    private fun formatDate(iso: String?): String {
        if (iso == null) return "—"
        return try {
            val i = Instant.parse(iso)
            DateTimeFormatter.ISO_LOCAL_DATE.format(i.atZone(java.time.ZoneId.systemDefault()))
        } catch (_: Exception) { iso }
    }

    private fun observeFiltersFetchWork() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(FetchFiltersWorker.ONE_TIME_WORK_NAME)
            .observe(viewLifecycleOwner) { workInfos ->
                val finished = workInfos.any { it.state.isFinished }
                if (finished) {
                    updateLastFiltersUpdateText()
                    updateFiltersStatusText()
                }
            }
    }

    private fun updateFiltersStatusText() {
        val filters = SmsFilter.getFilters(requireContext())
        binding.filtersStatusText.visibility = View.VISIBLE
        binding.filtersStatusText.text = if (filters != null && filters.isNotEmpty()) {
            getString(R.string.dashboard_filters_status_loaded)
        } else {
            getString(R.string.dashboard_filters_status_not_loaded)
        }
    }

    private fun updateLastFiltersUpdateText() {
        val last = FilterSystemState.getLastUpdated(requireContext())
        binding.lastFiltersUpdateText.text = if (last != null) {
            try {
                val millis = last.toLongOrNull() ?: 0L
                val instant = java.time.Instant.ofEpochMilli(millis)
                val zoned = instant.atZone(java.time.ZoneId.systemDefault())
                getString(R.string.dashboard_last_update, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(zoned))
            } catch (_: Exception) {
                getString(R.string.dashboard_last_update_never)
            }
        } else {
            getString(R.string.dashboard_last_update_never)
        }
    }

    private fun refreshDeniedPermissionsList() {
        val ctx = requireContext()
        val denied = mutableListOf<Pair<String, Int>>() // title string id, permission type
        if (!RuntimePermissions.hasSmsPermission(ctx)) {
            denied.add(Pair(getString(R.string.permission_sms), 0))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !RuntimePermissions.hasNotificationsPermission(ctx)) {
            denied.add(Pair(getString(R.string.permission_notifications), 1))
        }
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            denied.add(Pair(getString(R.string.permission_battery_optimization), 2))
        }
        // Background / app settings – we consider "denied" if notifications are off (same as PermissionsFragment)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !RuntimePermissions.hasNotificationsPermission(ctx)) {
            // Already in list as notifications
        } else {
            // Optional: could add "עבודה ברקע" as separate row that opens app settings
        }

        binding.deniedPermissionsList.removeAllViews()
        if (denied.isEmpty()) {
            binding.permissionsSectionTitle.visibility = View.VISIBLE
            binding.noDeniedPermissionsHint.visibility = View.VISIBLE
            binding.deniedPermissionsList.visibility = View.GONE
        } else {
            binding.permissionsSectionTitle.visibility = View.VISIBLE
            binding.noDeniedPermissionsHint.visibility = View.GONE
            binding.deniedPermissionsList.visibility = View.VISIBLE
            denied.forEach { (title, type) ->
                val card = makePermissionCard(title)
                card.setOnClickListener {
                    if (type == 2) {
                        openBatteryOptimizationSettings()
                    } else {
                        findNavController().navigate(R.id.action_DashboardFragment_to_PermissionsFragment)
                    }
                }
                binding.deniedPermissionsList.addView(card)
            }
        }
    }

    private fun makePermissionCard(title: String): MaterialCardView {
        val card = layoutInflater.inflate(R.layout.item_denied_permission, binding.deniedPermissionsList, false) as MaterialCardView
        card.findViewById<TextView>(R.id.permission_item_title).text = title
        return card
    }

    private fun openBatteryOptimizationSettings() {
        val pkg = requireContext().packageName
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$pkg")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            }
            try {
                startActivity(fallback)
            } catch (_: Exception) { }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
