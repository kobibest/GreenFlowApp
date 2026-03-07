package com.tanglycohort.greenflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.tanglycohort.greenflow.databinding.FragmentPermissionsBinding

class PermissionsFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results: Map<String, Boolean> ->
        if (!isAdded) return@registerForActivityResult
        refreshPermissionStatus()
        if (RuntimePermissions.hasRequiredRuntimePermissions(requireContext())) {
            findNavController().popBackStack()
        } else if (results.values.any { !it }) {
            findNavController().navigate(R.id.action_PermissionsFragment_to_PermissionsDeniedFragment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)

        binding.permissionSms.setOnClickListener { requestSmsPermission() }
        binding.permissionNotifications.setOnClickListener { requestNotificationsPermission() }
        binding.permissionBattery.setOnClickListener { openBatteryOptimizationSettings() }
        binding.permissionBackground.setOnClickListener { openAppSettings() }

        binding.permissionsRequestButton.setOnClickListener {
            requestAllNeededPermissions()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshPermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        if (!isAdded) return
        refreshPermissionStatus()
        if (findNavController().currentDestination?.id == R.id.PermissionsFragment &&
            RuntimePermissions.hasRequiredRuntimePermissions(requireContext())
        ) {
            findNavController().popBackStack()
        }
    }

    private fun refreshPermissionStatus() {
        val ctx = requireContext()
        binding.permissionSmsSwitch.isChecked = RuntimePermissions.hasSmsPermission(ctx)
        binding.permissionNotificationsSwitch.isChecked = RuntimePermissions.hasNotificationsPermission(ctx)
        binding.permissionBatterySwitch.isChecked = isIgnoringBatteryOptimizations()
        binding.permissionBackgroundSwitch.isChecked = RuntimePermissions.hasNotificationsPermission(ctx)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = requireContext().getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun requestSmsPermission() {
        if (RuntimePermissions.hasSmsPermission(requireContext())) return
        requestMultiplePermissions.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
    }

    private fun requestNotificationsPermission() {
        if (RuntimePermissions.hasNotificationsPermission(requireContext())) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestMultiplePermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun requestAllNeededPermissions() {
        val toRequest = mutableListOf<String>()
        if (!RuntimePermissions.hasSmsPermission(requireContext())) toRequest.add(Manifest.permission.RECEIVE_SMS)
        if (!RuntimePermissions.hasNotificationsPermission(requireContext()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (toRequest.isEmpty()) {
            if (isAdded && RuntimePermissions.hasRequiredRuntimePermissions(requireContext())) {
                findNavController().popBackStack()
            }
            return
        }
        requestMultiplePermissions.launch(toRequest.toTypedArray())
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

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) { }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
