package es.upm.ies.surco.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import es.upm.ies.surco.AppMain
import es.upm.ies.surco.BuildConfig
import es.upm.ies.surco.R
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.api.ApiPrivacyPolicyState
import es.upm.ies.surco.api.ApiUserSessionState
import es.upm.ies.surco.databinding.ActivityMainBinding
import es.upm.ies.surco.session_logging.LoggingSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActMain : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var navController: NavController

    private val appMain by lazy { application as AppMain }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureToolbar()
        configureNavigationDrawer()

        // Or if all permissions were already granted, start the session
        val arePermissionsOk = ActPermissions.Companion.allPermissionsGranted(this)
        if (!arePermissionsOk) {  // If any permission is not granted, go to permissions activity and wait for user to grant permissions
            val getAllPermissionsGranted =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                    if (result.resultCode != RESULT_OK) {
                        // If user did not grant permissions, close the app (this should not happen)
                        Toast.makeText(
                            this, "Permissions are required to continue", Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            getAllPermissionsGranted.launch(Intent(this, ActPermissions::class.java))
        }

        ApiActions.User.state.observe(this) { state ->
            updateDrawerOptionsMenu()
        }

        appMain.wasUploadedSuccessfully.observe(this) { wasUploadedSuccessfully ->
            if (wasUploadedSuccessfully) {  // If the data was uploaded successfully, show a message
                Toast.makeText(this, getString(R.string.upload_successful), Toast.LENGTH_SHORT)
                    .show()
            } // do nothing on else
        }

        // Observe the session state to keep the screen on during a session on Android 14 and higher
        // until we fix this issue in the future
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appMain.loggingSession.status.observe(this) { status ->
                when (status) {
                    LoggingSessionStatus.SESSION_ONGOING -> {
                        // Set screen to never turn off
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    LoggingSessionStatus.SESSION_TRIGGERABLE -> {
                        // Unset screen to never turn off
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    else -> {
                        // Do nothing
                    }
                }
            }
        }

        appMain.loggingSession.status.observe(this) {
            when (it) {
                LoggingSessionStatus.SESSION_ONGOING -> {
                    Toast.makeText(
                        this, getString(R.string.session_started), Toast.LENGTH_SHORT
                    ).show()
                }

                LoggingSessionStatus.SESSION_STOPPING -> {
                    Toast.makeText(
                        this, getString(R.string.session_stopped), Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {
                    // Do nothing
                }
            }
        }

        checkPrivacyPolicy()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.fragment_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        /** Handle navigation view item clicks here. */
        return when (item.itemId) {
            R.id.homeFragment -> {
                // Close the drawer
                binding.mainDrawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.homeFragment)
                true
            }

            R.id.nav_settings -> {
                binding.mainDrawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.fragSettings)
                true
            }

            R.id.nav_logout -> {
                binding.mainDrawerLayout.closeDrawers()
                ApiActions.User.logout()
                Toast.makeText(this, getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
                true
            }

            R.id.nav_login -> {
                binding.mainDrawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.fragLogin)
                true
            }

            R.id.nav_about -> {
                binding.mainDrawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.fragAbout)
                true
            }

            R.id.nav_help -> {
                openURL("${BuildConfig.SERVER_URL}/contact")
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun configureToolbar() {
        setSupportActionBar(binding.toolbar)
        actionBarDrawerToggle = ActionBarDrawerToggle(
            this,
            binding.mainDrawerLayout,
            binding.toolbar,
            R.string.toolbar_open,
            R.string.toolbar_close
        )
        binding.mainDrawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.isDrawerIndicatorEnabled = true
        actionBarDrawerToggle.syncState()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_main) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(navController.graph, binding.mainDrawerLayout)
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
    }

    private fun configureNavigationDrawer() {
        binding.navViewHost.setNavigationItemSelectedListener(this)
        updateDrawerOptionsMenu()
    }

    private fun updateDrawerOptionsMenu() {
        binding.navViewHost.post {
            // Login and logout buttons in the drawer
            val menuBtnLogin = binding.navViewHost.menu.findItem(R.id.nav_login)
            val menuBtnLogout = binding.navViewHost.menu.findItem(R.id.nav_logout)
            val isUserLoggedIn = ApiActions.User.state.value == ApiUserSessionState.LOGGED_IN
            menuBtnLogin.isVisible = isUserLoggedIn != true
            menuBtnLogout.isVisible = !menuBtnLogin.isVisible
            binding.navViewHost.invalidate()
        }
    }

    fun openURL(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening link: $url; ${e.message}")
            Toast.makeText(this, "Could not open: $url", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPrivacyPolicy() {
        // Privacy policy management
        when (ApiActions.PrivacyPolicy.state.value) {
            ApiPrivacyPolicyState.NEVER_PROMPTED -> {
                // Begin fragment transaction to prompt the user to accept the privacy policy
                // Handled by FragHome
            }

            ApiPrivacyPolicyState.ACCEPTED -> {
                // The user has accepted the privacy policy, start a coroutine to verify if the privacy policy has been updated
                pingServerAndTakeActions()
            }

            ApiPrivacyPolicyState.OUTDATED -> {
                // The user has accepted the privacy policy, but it has been updated since then
                // Handled by FragHome
            }

            ApiPrivacyPolicyState.REJECTED -> {
                // The user has rejected the privacy policy, we must not access the API unless requested
            }

            ApiPrivacyPolicyState.CONNECTION_ERROR -> {
                // An error occurred while checking the privacy policy earlier, so let's not navigate anywhere, but refresh for OUTDATED state in case it has been fixed
                pingServerAndTakeActions()
            }
        }
    }

    private fun pingServerAndTakeActions() {
        lifecycleScope.launch(Dispatchers.Default) {
            val actionFlags = ApiActions.Common.ping()
            if (ApiActions.Common.PingFlag.PRIVACY_POLICY_OUTDATED in actionFlags) {
                // Keep the latest status except for when it was updated
                if (ApiActions.PrivacyPolicy.state.value != ApiPrivacyPolicyState.REJECTED) {
                    // Unless explicitly rejected the privacy policy, update it
                    Log.i("ApiPrivacyPolicy", "Updating privacy policy due to new revision")
                    ApiActions.PrivacyPolicy.state_.postValue(ApiPrivacyPolicyState.OUTDATED)
                    ApiActions.PrivacyPolicy.updatePrivacyPolicy()
                }
            }
            if (ApiActions.Common.PingFlag.CLIENT_DEPRECATED_WARNING in actionFlags) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(applicationContext).apply {
                        setTitle(getString(R.string.client_deprecated_warning_title))
                        setMessage(getString(R.string.client_deprecated_warning_message))
                        setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        create().show()
                    }
                }
            }
            if (ApiActions.Common.PingFlag.CLIENT_OBSOLETE_ERROR in actionFlags) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(applicationContext).apply {
                        setTitle(getString(R.string.client_obsolete_error_title))
                        setMessage(getString(R.string.client_obsolete_error_message))
                        setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        create().show()
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "ActMain"
    }
}
