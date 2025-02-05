package com.example.beaconble.ui

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE
import android.bluetooth.BluetoothManager
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.os.Build
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

class FragHome : Fragment() {
    lateinit var viewModel: Lazy<FragHomeViewModel>

    // UI elements
    lateinit var beaconListView: ListView
    lateinit var beaconCountTextView: TextView
    lateinit var startStopSessionButton: FloatingActionButton
    lateinit var emptyAllButton: ImageButton
    lateinit var shareSessionButton: ImageButton
    lateinit var uploadSessionButton: ImageButton

    // Adapter for the list view
    lateinit var adapter: ListAdapterBeacons

    // Application instance
    lateinit var appMain: AppMain

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = requireActivity().getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth is enabled, good to go
        } else {
            // User dismissed or denied Bluetooth prompt
            promptEnableBluetooth()
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
        emptyAllButton = view.findViewById<ImageButton>(R.id.imBtnActionEmptyAll)
        shareSessionButton = view.findViewById<ImageButton>(R.id.imBtnActionShareSession)
        uploadSessionButton = view.findViewById<ImageButton>(R.id.imBtnActionUploadSession)

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
            // Check if Bluetooth is enabled
            if (!bluetoothAdapter.isEnabled) {
                // If Bluetooth is not enabled, prompt the user to enable it
                promptEnableBluetooth()
            }
            viewModel.value.toggleSession()
        }

        emptyAllButton.setOnClickListener {
            if (viewModel.value.rangedBeacons.value!!.isEmpty()) {
                // If there are no beacons, show a toast message and return
                Toast.makeText(
                    requireContext(),
                    getString(R.string.empty_session_nothing_to_do),
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

        shareSessionButton.setOnClickListener {
            // Check if there is data to export
            if (viewModel.value.rangedBeacons.value!!.isEmpty()) {
                // If there are no beacons, show a toast message and return
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_data_to_share),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // Call activity's share method
            (requireActivity() as ActMain).shareSession()
        }

        uploadSessionButton.setOnClickListener {
            // Check if there is data to upload
            if (AppMain.instance.loggingSession.getSessionFiles().isEmpty()) {
                // If there are no files, show a toast message and return
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_data_to_upload),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // Upload the session data
            viewModel.value.uploadAllSessions()
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
            startStopSessionButton.tooltipText = getString(R.string.stop_button)
            startStopSessionButton.contentDescription = getString(R.string.stop_button)
        } else {
            startStopSessionButton.setImageResource(R.drawable.triangle_start)
            startStopSessionButton.tooltipText = getString(R.string.start_button)
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

    /**
     * Prompts the user to enable Bluetooth via a system dialog.
     *
     * For Android 12+, [Manifest.permission.BLUETOOTH_CONNECT] is required to use
     * the [BluetoothAdapter.ACTION_REQUEST_ENABLE] intent.
     */
    private fun promptEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ActPermissions.Companion.permissionGranted(requireContext(), BLUETOOTH_CONNECT)
        ) {
            // Insufficient permission to prompt for Bluetooth enabling
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Intent(ACTION_REQUEST_ENABLE).apply {
                bluetoothEnablingResult.launch(this)
            }
        }
    }
}
