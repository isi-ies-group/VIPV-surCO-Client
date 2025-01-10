package com.example.beaconble.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
//import androidx.preference.SwitchPreferenceCompat
import com.example.beaconble.AppMain
import com.example.beaconble.BuildConfig
import com.example.beaconble.R
import kotlinx.coroutines.launch

class FragSettings : PreferenceFragmentCompat() {
    // lock for the test api endpoint button, so multiple requests are not sent before the first one finishes
    private var testingApiEndpoint = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        /*// Ge the upload only on wifi setting
        val switchPreference = findPreference<SwitchPreferenceCompat>("auto_upload_on_metered")
        // set callback to update the application service when the value changes
        switchPreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                Log.d("FragSettings", "Setting upload only on wifi to $newValue")
                // TODO AppMain.instance.setUploadOnlyOnWifi(newValue as Boolean)
                true
            }*/

        // Get the API URI setting
        val editTextPreference = findPreference<EditTextPreference>("api_uri")
        if (editTextPreference == null) {
            Log.w("FragSettings", "API URI setting not found")
        }
        // set the hint to the current value
        editTextPreference?.setOnBindEditTextListener{
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
                val isUp = AppMain.instance.testApiEndpoint()
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
}