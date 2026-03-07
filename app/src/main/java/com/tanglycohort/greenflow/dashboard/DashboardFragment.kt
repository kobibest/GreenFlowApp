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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.DeviceId
import com.tanglycohort.greenflow.FetchFiltersWorker
import com.tanglycohort.greenflow.data.repository.ProfilesRepository
import kotlinx.coroutines.launch
import com.tanglycohort.greenflow.FilterSystemState
import com.tanglycohort.greenflow.R
import com.tanglycohort.greenflow.RuntimePermissions
import com.tanglycohort.greenflow.SmsFilter
import com.tanglycohort.greenflow.databinding.FragmentDashboardBinding
import com.tanglycohort.greenflow.service.WebhookService
import io.github.jan.supabase.gotrue.auth

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        // 1. Toggle On/Off
        binding.mainSwitch.isChecked = WebhookService.isServiceEnabled(requireContext())
        binding.mainSwitch.setOnCheckedChangeListener { _, isChecked ->
            WebhookService.setServiceEnabled(requireContext(), isChecked)
        }

        // 2. Refresh filters + last update + forward-all-when-no-filters + filters status
        updateLastFiltersUpdateText()
        updateFiltersStatusText()
        binding.forwardAllWhenNoFiltersSwitch.isChecked = WebhookService.isForwardAllWhenNoFilters(requireContext())
        binding.forwardAllWhenNoFiltersSwitch.setOnCheckedChangeListener { _, isChecked ->
            WebhookService.setForwardAllWhenNoFilters(requireContext(), isChecked)
        }
        binding.refreshFiltersButton.setOnClickListener {
            FetchFiltersWorker.enqueueOnce(requireContext())
            Toast.makeText(requireContext(), R.string.main_refresh_filters_toast, Toast.LENGTH_SHORT).show()
            updateLastFiltersUpdateText()
            updateFiltersStatusText()
        }

        // 3. Denied permissions list – build dynamically and open Permissions screen on click
        refreshDeniedPermissionsList()
    }

    override fun onResume() {
        super.onResume()
        updateLastFiltersUpdateText()
        updateFiltersStatusText()
        refreshDeniedPermissionsList()
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
