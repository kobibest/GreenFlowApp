package com.tanglycohort.greenflow

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.navigation.NavigationView
import com.tanglycohort.greenflow.R
import com.tanglycohort.greenflow.bugreport.ReportBugDialogFragment
import com.tanglycohort.greenflow.debug.DebugAgentLog
import com.tanglycohort.greenflow.service.WebhookService
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val authRepository = com.tanglycohort.greenflow.data.repository.AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        // #region agent log
        DebugAgentLog.log("MainActivity.kt:onCreate", "MainActivity onCreate start", emptyMap(), "H2")
        // #endregion
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // #region agent log
        DebugAgentLog.log("MainActivity.kt:onCreate", "MainActivity after setContentView", emptyMap(), "H2")
        // #endregion

        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val navView = findViewById<NavigationView>(R.id.nav_view)

        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener { item ->
            val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val nav = host?.navController
            when (item.itemId) {
                R.id.menu_dashboard -> {
                    nav?.navigate(R.id.DashboardFragment, null, NavOptions.Builder()
                        .setPopUpTo(R.id.DashboardFragment, true)
                        .setLaunchSingleTop(true)
                        .build())
                }
                R.id.menu_activity -> {
                    nav?.navigate(R.id.ActivityFragment)
                }
                R.id.menu_profile -> {
                    nav?.navigate(R.id.ProfileFragment)
                }
                R.id.menu_subscription -> {
                    nav?.navigate(R.id.SubscriptionFragment)
                }
                R.id.menu_report_bug -> {
                    ReportBugDialogFragment().show(supportFragmentManager, "ReportBug")
                }
                R.id.menu_logout -> {
                    lifecycleScope.launch {
                        authRepository.signOut()
                        nav?.navigate(R.id.LoginFragment, null, NavOptions.Builder()
                            .setPopUpTo(R.id.DashboardFragment, true)
                            .setLaunchSingleTop(true)
                            .build())
                    }
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        fun setupNavGraph(hasSession: Boolean) {
            val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                ?: return
            val navController = host.navController
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            navGraph.setStartDestination(
                if (hasSession) R.id.DashboardFragment
                else R.id.LoginFragment
            )
            navController.graph = navGraph
        }

        lifecycleScope.launch {
            // #region agent log
            DebugAgentLog.log("MainActivity.kt:setupNavGraph", "launch start, getting sessionStatus", emptyMap(), "H3")
            // #endregion
            val status = authRepository.sessionStatus().first()
            // #region agent log
            DebugAgentLog.log("MainActivity.kt:setupNavGraph", "sessionStatus first done", mapOf("status" to status?.javaClass?.simpleName), "H3")
            // #endregion
            val hasSession = status is SessionStatus.Authenticated
            setupNavGraph(hasSession)
            // #region agent log
            DebugAgentLog.log("MainActivity.kt:setupNavGraph", "setupNavGraph done", mapOf("hasSession" to hasSession), "H3")
            // #endregion
        }

        lifecycleScope.launch {
            authRepository.sessionStatus().collectLatest { status ->
                if (status is SessionStatus.NotAuthenticated && authRepository.currentSession() == null) {
                    val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val nav = host?.navController ?: return@collectLatest
                    val currentId = nav.currentBackStackEntry?.destination?.id ?: return@collectLatest
                    if (currentId == R.id.LoginFragment || currentId == R.id.RegisterFragment) return@collectLatest
                    val options = NavOptions.Builder()
                        .setPopUpTo(R.id.DashboardFragment, true)
                        .setLaunchSingleTop(true)
                        .build()
                    nav.navigate(R.id.LoginFragment, null, options)
                }
            }
        }

        if (WebhookService.isPersistentBackgroundEnabled(this)) {
            val intent = Intent(this, GreenFlowForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
