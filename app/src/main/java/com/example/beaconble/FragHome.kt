package com.example.beaconble

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FragHome : Fragment() {
    lateinit var viewModel: Lazy<FragHomeViewModel>

    lateinit var postButton: FloatingActionButton

    //inicializacion de variables
    lateinit var beaconListView: ListView
    lateinit var beaconCountTextView: TextView
    lateinit var monitoringButton: Button
    lateinit var rangingButton: Button
    lateinit var beaconReferenceApplication: BeaconReferenceApplication
    var alertDialog: AlertDialog? = null
    lateinit var sensorData:SensorData
    var sensorDataList:MutableList<SensorData> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Create a ViewModel the first time the system creates this Class.
        // Re-created fragments receive the same ViewModel instance created by the first one.
        viewModel = viewModels<FragHomeViewModel>()

        // Inflate the layout for this fragment and find the IDs of the UI elements.
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        postButton = view.findViewById<FloatingActionButton>(R.id.uploadButton)
        beaconListView = view.findViewById<ListView>(R.id.beaconListView)
        beaconCountTextView = view.findViewById<TextView>(R.id.beaconCountTextView)

        // Assign observers and callbacks
        viewModel.value.exampleData.observe(viewLifecycleOwner) { data ->
            // Update the list
            beaconListView.adapter = ArrayAdapter(requireContext(), androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, data)
        }

        viewModel.value.topMessage.observe(viewLifecycleOwner) { message ->
            // Update the top message textview
            beaconCountTextView.text = message
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postButton.setOnClickListener {
            // findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
            // TODO("Implement post button")
            viewModel.value.sendTestData()
        }

        beaconCountTextView.text = getString(R.string.beacons_detected_zero)
    }
}
