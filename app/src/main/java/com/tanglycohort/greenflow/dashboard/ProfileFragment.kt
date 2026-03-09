package com.tanglycohort.greenflow.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.tanglycohort.greenflow.R
import com.tanglycohort.greenflow.data.repository.AuthRepository
import com.tanglycohort.greenflow.databinding.FragmentProfileBinding
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = com.tanglycohort.greenflow.supabase.SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: AuthRepository().getUserIdFromSession()
        if (userId == null) {
            findNavController().navigate(R.id.LoginFragment)
            return
        }
        viewModel.loadDashboard(userId)

        binding.profileSaveButton.setOnClickListener {
            viewModel.saveProfile(
                userId,
                binding.profileNameEdit.text?.toString(),
                binding.profilePhoneEdit.text?.toString()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                binding.profileProgress.visibility = if (state.loading) View.VISIBLE else View.GONE
                if (!state.loading) {
                    state.profile?.let { p ->
                        if (binding.profileNameEdit.text?.toString() != p.name) binding.profileNameEdit.setText(p.name)
                        if (binding.profileEmailEdit.text?.toString() != p.email) binding.profileEmailEdit.setText(p.email)
                        if (binding.profilePhoneEdit.text?.toString() != p.phone) binding.profilePhoneEdit.setText(p.phone)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is DashboardEvent.ShowError -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    DashboardEvent.NavigateToLogin -> { /* handled by Activity / drawer logout */ }
                    DashboardEvent.TrialStarted -> Toast.makeText(requireContext(), getString(R.string.trial_started_toast), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
