package es.upm.ies.surco.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import es.upm.ies.surco.BuildConfig
import es.upm.ies.surco.R
import es.upm.ies.surco.AppMain
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class FragSettings : PreferenceFragmentCompat() {
    // lock for the test api endpoint button, so multiple requests are not sent before the first one finishes
    private var testingApiEndpoint = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Set the callback to open the manage sessions fragment
        findPreference<Preference>("manage_sessions")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_fragManageSessions)
            true
        }

        // Set the callback to open the manage sessions fragment
        findPreference<Preference>("open_battery_optimization_settings")?.setOnPreferenceClickListener {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }

            true
        }

        // Set the callback to change the scan_interval
        findPreference<EditTextPreference>("scan_interval")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                try {
                    AppMain.Companion.instance.scanInterval = (newValue as String).toLong()
                } catch (_: NumberFormatException) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.settings_bluetooth_scan_interval_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@OnPreferenceChangeListener false
                }
                true
            }

        // Get the color theme setting
        val colorThemePreference = findPreference<ListPreference>("color_theme")
        if (colorThemePreference == null) {
            Log.w("FragSettings", "Color theme setting not found")
        }
        // set the callback to update the application theme when the value changes
        colorThemePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                AppMain.Companion.instance.setupTheme(newValue as String)
                true
            }

        // Get the API URI setting
        val editTextPreference = findPreference<EditTextPreference>("api_uri")
        if (editTextPreference == null) {
            Log.w("FragSettings", "API URI setting not found")
        }
        // set the hint to the current value
        editTextPreference?.setOnBindEditTextListener {
            it.hint = BuildConfig.SERVER_URL
        }
        // set callback to update the application API service when the value changes
        editTextPreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                AppMain.Companion.instance.setupApiService(newValue as String)
                true
            }

        // Get test api endpoint button preference
        val testApiEndpoint = findPreference<Preference>("api_test")
        // set callback to update the application API service when the value changes
        testApiEndpoint?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            Log.d("FragSettings", "Testing API endpoint")
            // if the button is already pressed, do nothing
            if (testingApiEndpoint) {
                return@OnPreferenceClickListener true
            }
            // set the flag to true
            testingApiEndpoint = true
            // show a toast that the test is running
            Toast.makeText(
                requireContext(),
                getString(R.string.settings_api_testing),
                Toast.LENGTH_SHORT
            ).show()
            lifecycleScope.launch {
                val isUp = AppMain.Companion.instance.testApiEndpoint()
                // set the flag to false
                testingApiEndpoint = false
                // show a toast with the result
                Log.d("FragSettings", "API endpoint is ${if (isUp) "up" else "down"}")
                Toast.makeText(
                    requireContext(),
                    if (isUp) getString(R.string.settings_api_valid) else getString(R.string.settings_api_invalid),
                    Toast.LENGTH_LONG
                ).show()
            }
            true
        }
    }

    companion object {
        @SuppressLint("BatteryLife")
        fun canHandleBatteryOptimizationIntent(context: Context): Boolean {
            val packageName = context.packageName
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            return intent.resolveActivity(context.packageManager) != null
        }
        val TAG: String = FragSettings::class.java.simpleName
    }
}