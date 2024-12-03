package com.example.beaconble.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.beaconble.R

class AboutFragment : Fragment() {
    private lateinit var versionTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get the version name from the manifest
        val versionInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        versionTextView = requireView().findViewById<TextView>(R.id.version_textview)
        // Set the version name and version code in the text view as: "Version: 1.0-1"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {  // versionInfo.versionCode was deprecated in API level 28. Use versionCodeLong instead.
            versionTextView.text = getString(R.string.version, versionInfo.versionName, versionInfo.longVersionCode)
        } else {
            versionTextView.text = getString(R.string.version, versionInfo.versionName, versionInfo.versionCode)
        }
    }
}