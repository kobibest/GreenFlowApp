package com.tanglycohort.greenflow.dashboard

import android.content.Intent
import android.net.Uri
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
import com.tanglycohort.greenflow.billing.BillingManager
import com.tanglycohort.greenflow.billing.ProductIds
import com.tanglycohort.greenflow.data.model.Plan
import com.tanglycohort.greenflow.data.repository.AuthRepository
import com.tanglycohort.greenflow.databinding.FragmentSubscriptionBinding
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

class SubscriptionFragment : Fragment() {

    private var _binding: FragmentSubscriptionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    private var billingManager: BillingManager? = null

    private fun formatDate(iso: String?): String {
        if (iso == null) return ""
        return try {
            val i = Instant.parse(iso)
            DateTimeFormatter.ISO_LOCAL_DATE.format(i.atZone(java.time.ZoneId.systemDefault()))
        } catch (_: Exception) { iso }
    }

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
        billingManager = BillingManager(requireContext()).also { it.connect() }
        val accessToken = SupabaseProvider.client.auth.currentSessionOrNull()?.accessToken
        viewModel.loadDashboard(
            userId,
            querySubscriptions = { callback -> billingManager?.queryCurrentSubscriptions(callback) },
            accessToken = accessToken,
            packageName = requireContext().packageName
        )

        binding.trialButton.setOnClickListener { viewModel.startTrial(userId) }
        binding.cancelSubscriptionButton.setOnClickListener {
            val packageName = requireContext().packageName
            val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=$packageName")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            Toast.makeText(requireContext(), getString(R.string.cancel_subscription_toast), Toast.LENGTH_LONG).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                binding.subscriptionProgress.visibility = if (state.loading) View.VISIBLE else View.GONE
                if (!state.loading) {
                    val status = viewModel.subscriptionDisplayStatus()
                    val sub = state.subscription
                    binding.subscriptionStatusText.text = when (status) {
                        SubscriptionDisplayStatus.ACTIVE -> {
                            if (sub != null) "מ־${formatDate(sub.startsAt)} עד ${formatDate(sub.endsAt)}" else ""
                        }
                        SubscriptionDisplayStatus.TRIAL -> {
                            val daysRemaining = sub?.endsAt?.let { end ->
                                runCatching {
                                    (java.time.Instant.parse(end).toEpochMilli() - System.currentTimeMillis()) / 86400000
                                }.getOrNull()?.toInt() ?: 0
                            } ?: 0
                            getString(R.string.subscription_trial_active, daysRemaining.coerceAtLeast(0))
                        }
                        SubscriptionDisplayStatus.CANCELLED_VALID -> sub?.endsAt?.let { getString(R.string.subscription_cancelled_valid, formatDate(it)) } ?: ""
                        SubscriptionDisplayStatus.GRACE_PERIOD -> getString(R.string.subscription_grace_period)
                        SubscriptionDisplayStatus.BLOCKED -> getString(R.string.status_blocked)
                        SubscriptionDisplayStatus.EXPIRED, SubscriptionDisplayStatus.TRIAL_EXPIRED -> getString(R.string.status_expired)
                        SubscriptionDisplayStatus.CANCELLED -> "בוטל"
                        SubscriptionDisplayStatus.INACTIVE -> getString(R.string.status_inactive)
                    }
                    val showTrial = viewModel.canShowTrialButton()
                    binding.trialButton.visibility = if (showTrial) View.VISIBLE else View.GONE
                    binding.trialSectionTitle.visibility = if (showTrial) View.VISIBLE else View.GONE
                    val showCancelButton = status == SubscriptionDisplayStatus.ACTIVE
                    val showRenewButton = status == SubscriptionDisplayStatus.CANCELLED_VALID
                    binding.cancelSubscriptionButton.visibility = if (showCancelButton || showRenewButton) View.VISIBLE else View.GONE
                    if (showRenewButton) {
                        binding.cancelSubscriptionButton.text = getString(R.string.btn_renew_subscription)
                        binding.cancelSubscriptionButton.setOnClickListener {
                            val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=${requireContext().packageName}")
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    } else {
                        binding.cancelSubscriptionButton.text = getString(R.string.btn_cancel_subscription)
                        binding.cancelSubscriptionButton.setOnClickListener {
                            val packageName = requireContext().packageName
                            val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=$packageName")
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                            Toast.makeText(requireContext(), getString(R.string.cancel_subscription_toast), Toast.LENGTH_LONG).show()
                        }
                    }
                    refreshPlansList(state.plans, userId)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is DashboardEvent.ShowError -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    DashboardEvent.NavigateToLogin -> { /* logout handled from drawer */ }
                    DashboardEvent.TrialStarted -> Toast.makeText(requireContext(), getString(R.string.trial_started_toast), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshPlansList(plans: List<Plan>, userId: String) {
        binding.plansList.removeAllViews()
        val billing = billingManager
        val activity = activity
        // Show only monthly plans (no annual subscription option)
        val monthlyPlans = plans.filter { (it.durationDays ?: 0) <= 35 }
        monthlyPlans.forEach { plan ->
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
                if (billing == null) {
                    Toast.makeText(requireContext(), getString(R.string.billing_plan_not_configured), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                // Immediate feedback so user sees the click was registered
                Toast.makeText(requireContext(), getString(R.string.billing_checking), Toast.LENGTH_SHORT).show()
                val act = requireActivity()
                billing.querySubscriptionProductDetails { productDetails ->
                    act.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        if (productDetails == null) {
                            Toast.makeText(requireContext(), getString(R.string.billing_not_available), Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }
                        billing.launchSubscriptionPurchase(
                        activity = act,
                        productDetails = productDetails,
                        onSuccess = { purchase ->
                            billing.acknowledgePurchaseIfNeeded(purchase) {
                                val token = SupabaseProvider.client.auth.currentSessionOrNull()?.accessToken
                                if (token != null) {
                                    viewModel.onPlayPurchaseSuccess(
                                        userId = userId,
                                        accessToken = token,
                                        purchaseToken = purchase.purchaseToken,
                                        productId = purchase.products.firstOrNull() ?: ProductIds.MONTHLY_SUB,
                                        packageName = requireContext().packageName
                                    )
                                }
                            }
                        },
                        onError = { msg -> if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() }
                    )
                }
            }
            }
            binding.plansList.addView(card)
        }
    }

    override fun onDestroyView() {
        billingManager?.endConnection()
        billingManager = null
        super.onDestroyView()
        _binding = null
    }
}
