package com.tanglycohort.greenflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.tanglycohort.greenflow.databinding.FragmentPermissionsDeniedBinding

class PermissionsDeniedFragment : Fragment() {
    private var _binding: FragmentPermissionsDeniedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsDeniedBinding.inflate(inflater, container, false)

        binding.tryAgainButton.setOnClickListener {
            if (isAdded) {
                findNavController().navigate(R.id.action_PermissionsDeniedFragment_to_PermissionsFragment)
            }
        }

        binding.appInfoButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", requireContext().packageName, null)
            intent.data = uri
            startActivity(intent)
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (isAdded && RuntimePermissions.hasRequiredRuntimePermissions(requireContext())) {
            findNavController().navigate(R.id.action_PermissionsDeniedFragment_to_DashboardFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
