package com.tanglycohort.greenflow.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.tanglycohort.greenflow.R
import com.tanglycohort.greenflow.data.model.Plan
import com.tanglycohort.greenflow.data.repository.AuthRepository
import com.tanglycohort.greenflow.databinding.FragmentSubscriptionBinding
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

class SubscriptionFragment : Fragment() {

    private var _binding: FragmentSubscriptionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubscriptionBinding.inflate(inflater, container, false)
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

        binding.trialButton.setOnClickListener { viewModel.startTrial(userId) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                binding.subscriptionProgress.visibility = if (state.loading) View.VISIBLE else View.GONE
                if (!state.loading) {
                    val status = viewModel.subscriptionDisplayStatus()
                    binding.subscriptionStatusText.text = when (status) {
                        SubscriptionDisplayStatus.ACTIVE, SubscriptionDisplayStatus.TRIAL -> {
                            val sub = state.subscription
                            if (sub != null) "מ־${formatDate(sub.startsAt)} עד ${formatDate(sub.endsAt)}" else ""
                        }
                        SubscriptionDisplayStatus.BLOCKED -> getString(R.string.status_blocked)
                        SubscriptionDisplayStatus.EXPIRED, SubscriptionDisplayStatus.TRIAL_EXPIRED -> getString(R.string.status_expired)
                        SubscriptionDisplayStatus.CANCELLED -> "בוטל"
                        SubscriptionDisplayStatus.INACTIVE -> getString(R.string.status_inactive)
                    }
                    binding.trialButton.visibility = if (viewModel.canShowTrialButton()) View.VISIBLE else View.GONE
                    refreshPlansList(state.plans, userId)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is DashboardEvent.ShowError -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    DashboardEvent.NavigateToLogin -> { /* logout handled from drawer */ }
                }
            }
        }
    }

    private fun refreshPlansList(plans: List<Plan>, userId: String) {
        binding.plansList.removeAllViews()
        plans.forEach { plan ->
            val card = layoutInflater.inflate(R.layout.item_plan, binding.plansList, false) as MaterialCardView
            card.findViewById<TextView>(R.id.planName).text = plan.name ?: ""
            val duration = plan.durationDays ?: 0
            val priceStr = plan.price?.let { "₪%.2f".format(it) } ?: ""
            card.findViewById<TextView>(R.id.planPriceDuration).text = if (duration > 0) {
                getString(R.string.plan_price_duration, priceStr, duration)
            } else {
                getString(R.string.plan_price_only, priceStr)
            }
            card.findViewById<View>(R.id.planSubscribeButton).setOnClickListener {
                viewModel.subscribeToPlan(userId, plan)
            }
            binding.plansList.addView(card)
        }
    }

    private fun formatDate(iso: String?): String {
        if (iso == null) return ""
        return try {
            val i = Instant.parse(iso)
            DateTimeFormatter.ISO_LOCAL_DATE.format(i.atZone(java.time.ZoneId.systemDefault()))
        } catch (_: Exception) { iso }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
