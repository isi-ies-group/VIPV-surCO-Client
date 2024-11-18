package com.example.beaconble

import android.app.AlertDialog
import android.os.Build
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.SharedPreferences
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.altbeacon.beacon.BeaconManager

class HomeFragment : Fragment() {

    lateinit var fab_settings: FloatingActionButton

    //inicializacion de variables
    lateinit var beaconListView: ListView
    lateinit var beaconCountTextView: TextView
    lateinit var monitoringButton: Button
    lateinit var rangingButton: Button
    lateinit var postButton: Button
    lateinit var beaconReferenceApplication: BeaconReferenceApplication
    var alertDialog: AlertDialog? = null
    lateinit var sensorData:SensorData
    var sensorDataList:MutableList<SensorData> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        fab_settings = view.findViewById<FloatingActionButton>(R.id.uploadButton)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fab_settings.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
        }
        settings()
    }


    private fun settings() {
        val context = requireContext()
        val sp = PreferenceManager.getDefaultSharedPreferences(context)


    }
}
