package com.example.beaconble.ui

import android.os.Bundle
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.beaconble.BeaconReferenceApplication
import com.example.beaconble.BuildConfig
import com.example.beaconble.R

class FragSettings : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Get the API URI setting
        val editTextPreference = findPreference<EditTextPreference>("api_uri")
        if (editTextPreference == null) {
            Log.w("FragSettings", "API URI setting not found")
        }
        // Callback to update the application API service when the value changes
        editTextPreference?.setOnBindEditTextListener { editText ->
            editText.setOnEditorActionListener { _, _, _ ->
                // Update the endpoint in the API service
                BeaconReferenceApplication.Companion.instance.setService(editText.text.toString())
                true
            }
        }
        editTextPreference?.setDefaultValue(BuildConfig.SERVER_URL)
    }

}