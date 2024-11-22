package com.example.beaconble

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class FragSettings : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

}