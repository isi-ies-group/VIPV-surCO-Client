package es.upm.ies.surco.ui

import android.Manifest
import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE
import android.bluetooth.BluetoothManager
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import es.upm.ies.surco.R
import es.upm.ies.surco.databinding.FragmentHomeBinding
import es.upm.ies.surco.AppMain
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.api.ApiPrivacyPolicyState
import es.upm.ies.surco.api.ApiUserSessionState
import es.upm.ies.surco.hideKeyboard
import es.upm.ies.surco.session_logging.BeaconSimplified
import es.upm.ies.surco.session_logging.LoggingSessionStatus

class FragHome : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FragHomeViewModel by viewModels(
        factoryProducer = {
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        })

    // Adapter for the list view
    lateinit var adapter: ListAdapterBeacons

    // Application instance
    lateinit var appMain: AppMain

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            requireActivity().getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            // User dismissed or denied Bluetooth prompt
            promptEnableBluetooth()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Get the application instance
        appMain = requireActivity().application as AppMain

        // Create the adapter for the list view and assign it to the list view.
        adapter = ListAdapterBeacons(requireContext(), ArrayList(), viewLifecycleOwner)
        binding.beaconListView.adapter = adapter

        // Set the start stop button text and icon according to the session state
        updateStartStopButton(
            viewModel.loggingSessionStatus.value ?: LoggingSessionStatus.SESSION_STOPPING
        )

        // Assign observers and callbacks to the ViewModel's LiveData objects.
        viewModel.rangedBeacons.observe(viewLifecycleOwner) { beacons ->
            adapter.updateData(beacons)
        }

        binding.beaconListView.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                val beacon = parent.getItemAtPosition(position) as BeaconSimplified
                val beaconId = beacon.id
                Log.d("FragHome", "Beacon clicked: $beaconId")
                // navigate to the details fragment, passing the beacon ID
                findNavController().navigate(
                    R.id.action_homeFragment_to_fragBeaconDetails, Bundle().apply {
                        putString("beaconId", beaconId.toString())
                    })
            }

        viewModel.nBeaconsOnline.observe(viewLifecycleOwner) { n ->
            updateBeaconCountTextView(
                n, viewModel.loggingSessionStatus.value == LoggingSessionStatus.SESSION_ONGOING
            )
            adapter.updateData(viewModel.rangedBeacons.value!!)
        }

        viewModel.loggingSessionStatus.observe(viewLifecycleOwner) { status ->
            updateStartStopButton(status)
            updateBeaconCountTextView(
                viewModel.nBeaconsOnline.value!!, status == LoggingSessionStatus.SESSION_ONGOING
            )
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboard() // Close the virtual keyboard

        privacyPolicyCheckAndNavigation()

        // If the user has never logged in and privacy policy is accepted, navigate to the login fragment
        if (ApiActions.User.state.value == ApiUserSessionState.NEVER_LOGGED_IN && ApiActions.PrivacyPolicy.state.value == ApiPrivacyPolicyState.ACCEPTED) {
            ApiActions.User.setOfflineMode()  // Avoid returning to login screen on loop by back pressing
            findNavController().navigate(R.id.action_homeFragment_to_fragLogin)
        }

        // Set click listeners for the buttons
        binding.startStopSessionButton.setOnClickListener {
            if (viewModel.loggingSessionStatus.value == LoggingSessionStatus.SESSION_TRIGGERABLE) {
                checkAndStartSession()
            } else {
                viewModel.toggleSession()
            }
        }

        binding.imBtnActionUploadSession.setOnClickListener {
            // Check if there is data to upload
            if (appMain.loggingSession.getSessionFiles().isEmpty()) {
                // If there are no files, show a toast message and return
                Toast.makeText(
                    requireContext(), getString(R.string.no_data_to_upload), Toast.LENGTH_SHORT
                ).show()
            } else if (ApiActions.User.state.value == ApiUserSessionState.NOT_LOGGED_IN || ApiActions.User.state.value == ApiUserSessionState.NEVER_LOGGED_IN) {
                // If the user is not logged in, show a toast message and return
                Toast.makeText(
                    requireContext(), getString(R.string.session_not_active), Toast.LENGTH_SHORT
                ).show()
            } else {
                // Toast that the data is being uploaded
                Toast.makeText(
                    requireContext(), getString(R.string.uploading_session_data), Toast.LENGTH_SHORT
                ).show()
                // Upload the session data
                viewModel.uploadAllSessions()
            }
        }

        binding.imBtnActionManageSessions.setOnClickListener {
            // Navigate to the manage sessions fragment
            findNavController().navigate(R.id.action_homeFragment_to_fragManageSessions)
        }

        binding.beaconCountTextView.text = getString(R.string.beacons_detected_zero)
    }

    private fun privacyPolicyCheckAndNavigation() {
        // Privacy policy management
        when (ApiActions.PrivacyPolicy.state.value) {
            ApiPrivacyPolicyState.NEVER_PROMPTED -> {
                // Begin fragment transaction to prompt the user to accept the privacy policy
                findNavController().navigate(R.id.action_homeFragment_to_privacyPolicyFragment)
            }

            ApiPrivacyPolicyState.ACCEPTED -> {
                // The user has accepted the privacy policy, start a coroutine to verify if the privacy policy has been updated
                // Handled by ActMain
            }

            ApiPrivacyPolicyState.OUTDATED -> {
                // The user has accepted the privacy policy, but it has been updated since then
                findNavController().navigate(R.id.action_homeFragment_to_privacyPolicyFragment)
            }

            ApiPrivacyPolicyState.REJECTED -> {
                // The user has rejected the privacy policy, we must not access the API unless requested
            }

            ApiPrivacyPolicyState.CONNECTION_ERROR -> {
                // An error occurred while checking the privacy policy earlier, so let's not navigate anywhere, but refresh for OUTDATED state in case it has been fixed
                // Handled by ActMain
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Updates the start/stop session button icon according to the session state, as well as the
     * content description.
     * @param status The current session status.
     */
    private fun updateStartStopButton(status: LoggingSessionStatus) {
        when (status) {
            LoggingSessionStatus.SESSION_ONGOING -> {
                binding.startStopSessionButton.setImageResource(R.drawable.square_stop)
                binding.startStopSessionButton.tooltipText = getString(R.string.stop_button)
                binding.startStopSessionButton.contentDescription = getString(R.string.stop_button)

                binding.startStopSessionButton.isEnabled = true
                binding.startStopSessionButton.isClickable = true
            }

            LoggingSessionStatus.SESSION_TRIGGERABLE -> {
                binding.startStopSessionButton.setImageResource(R.drawable.triangle_start)
                binding.startStopSessionButton.tooltipText = getString(R.string.start_button)
                binding.startStopSessionButton.contentDescription = getString(R.string.start_button)

                binding.startStopSessionButton.isEnabled = true
                binding.startStopSessionButton.isClickable = true
            }

            LoggingSessionStatus.SESSION_STOPPING -> {
                // Just disable the button
                binding.startStopSessionButton.isEnabled = false
                binding.startStopSessionButton.isClickable = false
            }
        }
    }

    /**
     * Updates the text view with the number of beacons detected, and whether the session is paused
     * or not.
     * @param nBeacons The number of beacons detected.
     * @param isSessionActive True if the session is active, false otherwise.
     */
    private fun updateBeaconCountTextView(nBeacons: Int, isSessionActive: Boolean) {
        if (isSessionActive) {
            // Update the top message textview to show the number of beacons detected
            if (nBeacons == 0) {
                binding.beaconCountTextView.text = getString(R.string.beacons_detected_zero)
            } else {
                binding.beaconCountTextView.text =
                    getString(R.string.beacons_detected_nonzero, nBeacons)
            }
        } else {
            binding.beaconCountTextView.text = getString(R.string.beacons_detected_paused)
        }
    }

    /**
     * Prompts the user to enable Bluetooth via a system dialog.
     *
     * For Android 12+, BLUETOOTH_CONNECT is required to use
     * the [BluetoothAdapter.ACTION_REQUEST_ENABLE] intent.
     */
    private fun promptEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 /* Android 12+ */ && !ActPermissions.Companion.permissionGranted(
                requireContext(), Manifest.permission.BLUETOOTH_SCAN
            )
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

    /**
     * Alerts the user if the compass precision is low and asks them to recalibrate it.
     */
    private fun promptAlertOnLowCompassPrecision() {
        appMain.sensorAccuracyValue.value?.let {
            if (it <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                val builder = AlertDialog.Builder(requireContext())
                builder.setTitle(getString(R.string.low_compass_precision_title))
                builder.setMessage(getString(R.string.low_compass_precision_message))
                builder.setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    dialog.dismiss()
                }
                builder.create().show()
            }
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permissionName, isGranted) ->
            if (!isGranted) {
                val permissionHumanName = permissionName.split(".").last()
                Toast.makeText(
                    requireContext(), "Permission required: $permissionHumanName", Toast.LENGTH_LONG
                ).show()
            }
        }

        // After processing all permissions, check if we can start the session
        if (viewModel.loggingSessionStatus.value == LoggingSessionStatus.SESSION_TRIGGERABLE) {
            checkAndStartSession()
        }
    }

    private fun checkAndStartSession() {
        // Check all required permissions
        val hasLocationPerms = ActPermissions.groupPermissionsGranted(
            requireContext(), "Location"
        )

        val hasBluetoothPerms = ActPermissions.groupPermissionsGranted(
            requireContext(), "Bluetooth"
        )

        val hasForegroundPerms =
            ActPermissions.groupPermissionsGranted(requireContext(), "ForegroundService")

        if (hasLocationPerms && hasBluetoothPerms && hasForegroundPerms) {
            promptEnableBluetooth()
            promptAlertOnLowCompassPrecision()
            viewModel.toggleSession()
        } else {
            // Request missing permissions
            val permissionsToRequest = mutableListOf<String>()

            if (!hasLocationPerms) {
                if (ActPermissions.permissionsByGroupMap["Location"] != null) {
                    permissionsToRequest.addAll(ActPermissions.permissionsByGroupMap["Location"]!!)
                }
            }

            if (!hasBluetoothPerms) {
                if (ActPermissions.permissionsByGroupMap["Bluetooth"] != null) {
                    permissionsToRequest.addAll(ActPermissions.permissionsByGroupMap["Bluetooth"]!!)
                }
            }

            if (!hasForegroundPerms && Build.VERSION.SDK_INT >= 28 /* Android 9+ */) {
                if (ActPermissions.permissionsByGroupMap["ForegroundService"] != null) {
                    permissionsToRequest.addAll(ActPermissions.permissionsByGroupMap["ForegroundService"]!!)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions.launch(permissionsToRequest.toTypedArray())
            }
        }
    }
}
