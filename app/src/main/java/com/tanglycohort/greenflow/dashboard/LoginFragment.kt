package com.tanglycohort.greenflow.dashboard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.R
import com.tanglycohort.greenflow.databinding.FragmentLoginBinding
import com.tanglycohort.greenflow.debug.DebugAgentLog
import com.tanglycohort.greenflow.service.WebhookService
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // #region agent log
        DebugAgentLog.log("LoginFragment.kt:onViewCreated", "Login onViewCreated start", emptyMap(), "H4")
        // #endregion
        binding.loginButton.setOnClickListener {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            binding.root.findFocus()?.let { focus -> imm?.hideSoftInputFromWindow(focus.windowToken, 0) }
                ?: imm?.hideSoftInputFromWindow(binding.passwordEdit.windowToken, 0)
            binding.root.requestFocus()
            binding.root.post {
                _binding ?: return@post
                val email = binding.emailEdit.text?.toString()?.trim() ?: ""
                val password = binding.passwordEdit.text?.toString() ?: ""
                viewModel.login(email, password)
            }
        }
        binding.registerLink.setOnClickListener {
            findNavController().navigate(R.id.action_LoginFragment_to_RegisterFragment)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is LoginEvent.NavigateToDashboard -> {
                        val ctx = requireContext()
                        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                        if (userId != null) {
                            WebhookService.setUserId(ctx, userId)
                            if (WebhookService.getWebhookUrl(ctx) == null) {
                                WebhookService.setWebhookUrl(ctx, BuildConfig.SERVER_BASE_URL + "/functions/v1/sms-webhook")
                            }
                        }
                        val navController = try { findNavController() } catch (e: Exception) { null }
                            ?: (requireActivity().supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)?.navController
                        Handler(Looper.getMainLooper()).post {
                            navController?.navigate(R.id.action_LoginFragment_to_DashboardFragment)
                        }
                    }
                    is LoginEvent.ShowError -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.loginButton.isEnabled = !state.loading
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
