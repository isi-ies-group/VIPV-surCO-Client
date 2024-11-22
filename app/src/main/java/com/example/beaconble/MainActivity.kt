package com.example.beaconble


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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    lateinit var toolbar: Toolbar
    lateinit var drawerLayout: DrawerLayout

    lateinit var navController : NavController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        configureToolbar()  // setups .toolbar, .drawerLayout, .actionBarDrawerToggle, .navController
        configureNavigationDrawer()

        checkPermissionsAndTransferToViewIfNeeded()
    }

    private fun checkPermissionsAndTransferToViewIfNeeded() {
        val arePermissionsOk = BeaconScanPermissionsActivity.allPermissionsGranted(this)
        if (!arePermissionsOk) {  // If any permission is not granted, go to permissions activity and wait for user to grant permissions
            val getAllPermissionsGranted = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode != RESULT_OK) {
                    // If user did not grant permissions, close the app (this should not happen)
                    Toast.makeText(this, "Permissions are required to continue", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            getAllPermissionsGranted.launch(Intent(this, BeaconScanPermissionsActivity::class.java))

        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.fragment_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        /** Handle navigation view item clicks here. */
        Log.d(TAG, "onNavigationItemSelected")
        Log.d(TAG, supportActionBar.toString())
        return when (item.itemId) {
            R.id.homeFragment -> {
                // Close the drawer
                drawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.homeFragment)
                Log.d(TAG, "Home from drawer")
                true
            }
            R.id.nav_settings -> {  // Settings
                // Close the drawer
                drawerLayout.closeDrawers()
                findNavController(R.id.fragment_main).navigate(R.id.settingsFragment)
                Log.d(TAG, "Settings from drawer")
                true
            }
            R.id.nav_logout -> {  // Logout
                // TODO()
                // findNavController(R.id.homeFragment).navigateUp()
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
        actionBarDrawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.toolbar_open, R.string.toolbar_close)
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
        drawerLayout = findViewById<DrawerLayout>(R.id.main_drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view_host)
        navView.setNavigationItemSelectedListener(this)
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

    companion object {
        const val TAG = "MainActivity"
    }  // companion object
}
