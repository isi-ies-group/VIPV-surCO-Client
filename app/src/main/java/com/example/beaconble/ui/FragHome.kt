package com.example.beaconble.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.beaconble.AppMain
import com.example.beaconble.BeaconSimplified
import com.example.beaconble.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.time.Instant

class FragHome : Fragment() {
    lateinit var viewModel: Lazy<FragHomeViewModel>

    // UI elements
    lateinit var beaconListView: ListView
    lateinit var beaconCountTextView: TextView
    lateinit var startStopSessionButton: FloatingActionButton
    lateinit var emptyAllButton: ImageButton
    lateinit var exportAllButton: ImageButton

    // Adapter for the list view
    lateinit var adapter: ListAdapterBeacons

    // Application instance
    lateinit var appMain: AppMain

    // Activity result contract for the file picker
    private val activityResultContract =
        registerForActivityResult(ActivityResultContracts.CreateDocument(mimeType = "text/plain")) { result ->
            if (result != null) {
                // Then call the exportAll method from the ViewModel with the file as parameter
                Log.d("FragHome", "Exporting all data to $result")
                val sanitizedUri = result.toString().replace("content://", "")
                viewModel.value.exportAll(result)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Create a ViewModel the first time the system creates this Class.
        // Re-created fragments receive the same ViewModel instance created by the first one.
        viewModel = viewModels<FragHomeViewModel>()

        // Get the application instance
        appMain = AppMain.Companion.instance

        // Inflate the layout for this fragment and find the IDs of the UI elements.
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        beaconListView = view.findViewById<ListView>(R.id.beaconListView)
        beaconCountTextView = view.findViewById<TextView>(R.id.beaconCountTextView)
        startStopSessionButton =
            view.findViewById<FloatingActionButton>(R.id.startStopSessionButton)
        emptyAllButton = view.findViewById<ImageButton>(R.id.imageButtonActionEmptyAll)
        exportAllButton = view.findViewById<ImageButton>(R.id.imageButtonActionExportAll)

        // Create the adapter for the list view and assign it to the list view.
        adapter = ListAdapterBeacons(requireContext(), ArrayList())
        beaconListView.adapter = adapter

        // Set the start stop button text and icon according to the session state
        updateStartStopButton(appMain.isSessionActive.value!!)

        // Assign observers and callbacks to the ViewModel's LiveData objects.
        viewModel.value.rangedBeacons.observe(viewLifecycleOwner) { beacons ->
            adapter.updateData(beacons)
        }

        beaconListView.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                val beacon = parent.getItemAtPosition(position) as BeaconSimplified
                val beaconId = beacon.id
                Log.d("FragHome", "Beacon clicked: $beaconId")
                // navigate to the details fragment, passing the beacon ID
                findNavController().navigate(
                    R.id.action_homeFragment_to_fragBeaconDetails,
                    Bundle().apply {
                        putString("beaconId", beaconId.toString())
                    })
            }

        viewModel.value.nRangedBeacons.observe(viewLifecycleOwner) { n ->
            updateBeaconCountTextView(n, appMain.isSessionActive.value!!)
        }

        viewModel.value.isSessionActive.observe(viewLifecycleOwner) { isSessionActive ->
            updateStartStopButton(isSessionActive)
            updateBeaconCountTextView(viewModel.value.nRangedBeacons.value!!, isSessionActive)
            // Show a toast message to indicate whether the session has started or stopped
            if (isSessionActive) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.session_started),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.session_stopped),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        return view  // Return the view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set click listeners for the buttons
        startStopSessionButton.setOnClickListener {
            viewModel.value.toggleSession()
        }

        emptyAllButton.setOnClickListener {
            if (viewModel.value.rangedBeacons.value!!.isEmpty()) {
                // If there are no beacons, show a toast message and return
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_data_to_empty),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // Create alertDialog to confirm the action
            val alertDialog = AlertDialog.Builder(requireContext())
            alertDialog.setTitle(getString(R.string.empty_all_data))
            alertDialog.setMessage(getString(R.string.empty_all_data_confirmation))
            alertDialog.setPositiveButton(getString(R.string.yes)) { dialog, which ->
                viewModel.value.emptyAll()
            }
            alertDialog.setNegativeButton(getString(R.string.no)) { dialog, which ->
            }
            alertDialog.show()
        }

        exportAllButton.setOnClickListener {
            // Check if there is data to export
            if (viewModel.value.rangedBeacons.value!!.isEmpty()) {
                // If there are no beacons, show a toast message and return
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_data_to_export),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // Open the file picker intention for document files
            val filename = "VIPV_${Instant.now()}.txt"
            activityResultContract.launch(filename)
        }

        beaconCountTextView.text = getString(R.string.beacons_detected_zero)
    }

    /**
     * Updates the start/stop session button icon according to the session state, as well as the
     * content description.
     * @param isSessionActive True if the session is active, false otherwise.
     */
    private fun updateStartStopButton(isSessionActive: Boolean) {
        if (isSessionActive) {
            startStopSessionButton.setImageResource(R.drawable.square_stop)
            startStopSessionButton.contentDescription = getString(R.string.stop_button)
        } else {
            startStopSessionButton.setImageResource(R.drawable.triangle_start)
            startStopSessionButton.contentDescription = getString(R.string.start_button)
        }
    }

    /**
     * Updates the text view with the number of beacons detected, and whether the session is paused
     * or not.
     * @param nRangedBeacons The number of beacons detected.
     * @param isSessionActive True if the session is active, false otherwise.
     */
    private fun updateBeaconCountTextView(nRangedBeacons: Int, isSessionActive: Boolean) {
        if (isSessionActive) {
            // Update the top message textview to show the number of beacons detected
            if (nRangedBeacons == 0) {
                beaconCountTextView.text = getString(R.string.beacons_detected_zero)
            } else {
                beaconCountTextView.text =
                    getString(R.string.beacons_detected_nonzero, nRangedBeacons)
            }
        } else {
            beaconCountTextView.text = getString(R.string.beacons_detected_paused)
        }
    }
}
