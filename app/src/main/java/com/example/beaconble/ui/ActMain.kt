package com.example.beaconble.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.beaconble.ApiUserSessionState
import com.example.beaconble.AppMain
import com.example.beaconble.BuildConfig
import com.example.beaconble.R
import com.google.android.material.navigation.NavigationView
import kotlin.concurrent.thread


class ActMain : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    lateinit var toolbar: Toolbar
    lateinit var drawerLayout: DrawerLayout

    lateinit var navController: NavController
    lateinit var navView: NavigationView

    val app = AppMain.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        configureToolbar()  // setups .toolbar, .drawerLayout, .actionBarDrawerToggle, .navController, .navView
        configureNavigationDrawer()  // setups .navView, .menuBtnLogin, .menuBtnLogout

        // Check if all permissions are granted
        // If not, go to permissions activity and wait for user to grant permissions, so the session can be started later by the user
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

        checkNeedFirstLogin()

        app.apiUserSession.lastKnownState.observeForever { state ->
            updateDrawerOptionsMenu()
        }

        // Observe the session state to keep the screen on during a session on Android 14 and higher
        // until we fix this issue in the future
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            app.isSessionActive.observeForever { isActive ->
                if (isActive) {
                    // Set screen to never turn off
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    // Unset screen to never turn off
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /**
     * Check if this is the first time the user opens the app and transfer to login in that case
     */
    private fun checkNeedFirstLogin() {
        // loading of the user session from shared preferences may change the state of the user session
        // NEVER_LOGGED_IN is the default state
        val userMayWantToLogin =
            app.apiUserSession.lastKnownState.value == ApiUserSessionState.NEVER_LOGGED_IN
        if (userMayWantToLogin) {
            // Navigate to login fragment
            supportFragmentManager.findFragmentById(R.id.fragment_main)?.findNavController()
                ?.navigate(R.id.fragLogin)
        }
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
                drawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.homeFragment)
                true
            }

            R.id.nav_settings -> {  // Settings
                // Close the drawer
                drawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.settingsFragment)
                true
            }

            R.id.nav_logout -> {  // Logout
                // Close the drawer
                drawerLayout.closeDrawers()
                // Logout
                app.apiUserSession.logout()
                // Show a toast
                Toast.makeText(this, getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
                true
            }

            R.id.nav_login -> {  // Login
                // Close the drawer
                drawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.fragLogin)
                true
            }

            R.id.nav_about -> {  // About
                // Close the drawer
                drawerLayout.closeDrawers()
                // Open about page fragment
                findNavController(R.id.fragment_main).navigate(R.id.aboutFragment)
                true
            }

            R.id.nav_help -> {  // Help
                // TODO("Review")
                openURL("https://github.com/isi-ies-group/VIPV-Data-Crowdsourcing-Client")
                // findNavController(R.id.fragment_main).navigate(R.id.helpFragment)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun configureToolbar() {
        toolbar = findViewById<Toolbar>(R.id.toolbar)
        drawerLayout = findViewById<DrawerLayout>(R.id.main_drawer_layout)
        actionBarDrawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.toolbar_open, R.string.toolbar_close
        )
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.isDrawerIndicatorEnabled = true
        actionBarDrawerToggle.syncState()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_main) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        toolbar.setupWithNavController(navController, appBarConfiguration)
    }

    private fun configureNavigationDrawer() {
        navView = findViewById<NavigationView>(R.id.nav_view_host)
        navView.setNavigationItemSelectedListener(this)

        // Only show login or logout (hides the other one)
        updateDrawerOptionsMenu()
    }

    private fun updateDrawerOptionsMenu() {
        navView.post(Runnable {
            // Login and logout buttons in the drawer
            val menuBtnLogin = navView.menu.findItem(R.id.nav_login)
            val menuBtnLogout = navView.menu.findItem(R.id.nav_logout)
            val isUserLoggedIn =
                app.apiUserSession.lastKnownState.value == ApiUserSessionState.LOGGED_IN
            menuBtnLogin.isVisible = isUserLoggedIn != true
            menuBtnLogout.isVisible = !menuBtnLogin.isVisible
            navView.invalidate()
            Log.i(TAG, "Set visibilities: ${menuBtnLogin.isVisible}, ${menuBtnLogout.isVisible}")
        })
    }

    fun openURL(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening link: $url; ${e.message}")
            Toast.makeText(this, "Could not open: $url", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Save the session to a file and open the send file dialog to share it.
     */
    fun shareSession() {
        thread {
            val file = app.loggingSession.saveSession()
            if (file == null) {
                runOnUiThread {
                    Toast.makeText(
                        this, getString(R.string.no_data_to_share), Toast.LENGTH_SHORT
                    ).show()
                }
                return@thread
            }
            val uri =
                FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileProvider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Share session data"))
        }
    }

    companion object {
        const val TAG = "ActMain"
    }  // companion object
}
