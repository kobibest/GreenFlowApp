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
import com.tanglycohort.greenflow.databinding.FragmentRegisterBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.registerButton.setOnClickListener {
            viewModel.register(
                binding.nameEdit.text?.toString() ?: "",
                binding.phoneEdit.text?.toString() ?: "",
                binding.emailEdit.text?.toString() ?: "",
                binding.passwordEdit.text?.toString() ?: ""
            )
        }
        binding.loginLink.setOnClickListener {
            findNavController().navigate(R.id.action_RegisterFragment_to_LoginFragment)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    RegisterEvent.ShowCheckEmailMessage ->
                        Toast.makeText(requireContext(), R.string.msg_check_email, Toast.LENGTH_LONG).show()
                    RegisterEvent.NavigateToLogin ->
                        findNavController().navigate(R.id.action_RegisterFragment_to_LoginFragment)
                    is RegisterEvent.ShowError -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.registerButton.isEnabled = !state.loading
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
