package com.tanglycohort.greenflow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.tanglycohort.greenflow.R
import com.tanglycohort.greenflow.databinding.FragmentPermissionsEduBinding

class PermissionsEduFragment : Fragment() {
    private var _binding: FragmentPermissionsEduBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // #region agent log
        AppLog.d(TAG, "PermissionsEduFragment onCreateView start")
        // #endregion
        _binding = FragmentPermissionsEduBinding.inflate(inflater, container, false)

        binding.tryAgainButton.setOnClickListener {
            findNavController().navigate(R.id.action_PermissionsEduFragment_to_PermissionsFragment)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "GF_DEBUG"
    }
}
