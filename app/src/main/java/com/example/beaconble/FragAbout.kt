package com.example.beaconble

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class AboutFragment : Fragment() {
    private lateinit var versionTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get the version name from the manifest
        val versionName = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        versionTextView = requireView().findViewById<TextView>(R.id.version_textview)
        versionTextView.text = getString(R.string.version, versionName)
    }
}