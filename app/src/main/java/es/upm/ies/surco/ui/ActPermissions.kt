package es.upm.ies.surco.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View.OnClickListener
import android.widget.TableRow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import es.upm.ies.surco.R
import es.upm.ies.surco.databinding.ActivityPermissionsBinding
import kotlin.text.get

class PermissionsRowAtomicHandler(
    val show: Boolean,
    val rowUI: TableRow,
    val buttonUI: MaterialButton,
) {
    // Group of permissions that are related to a single UI element
    // Ease access, management and UI interaction
    // manifestPermissions: Array of permissions that are related to the UI element (will depend on the target version)
    // rowUI: TableRow that contains the UI element
    // buttonUI: MaterialButton that is the UI element

    init {
        if (!show) {
            // Hide the UI element if there are no permissions, as it is not needed
            hide()
        }
    }

    fun hide() {
        // Hide the UI element
        rowUI.visibility = TableRow.GONE
    }

    fun disable() {
        // Disable the button element
        buttonUI.isEnabled = false
    }

    fun setOnCheckedChangeListener(clickedCallback: OnClickListener) {
        // Set the callback for the button
        buttonUI.setOnClickListener(clickedCallback)
    }
}

class ActPermissions : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionsBinding

    private lateinit var rowPermissionsLocalization: PermissionsRowAtomicHandler
    private lateinit var rowPermissionsBluetooth: PermissionsRowAtomicHandler
    private lateinit var rowPermissionsNotifications: PermissionsRowAtomicHandler
    private lateinit var rowDisableBatteryOptimization: PermissionsRowAtomicHandler

    private lateinit var mapOfRowHandlers: Map<String, PermissionsRowAtomicHandler>

    private var isIgnoringBatteryOptimizations = false

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Map<String, Boolean> where String=Key permission and Boolean=result
        permissions.entries.forEach { (permissionName, isGranted) ->
            Log.d(TAG, "$permissionName permission granted: $isGranted")
            if (!isGranted) {
                // Get the last part of the permission name
                val permissionHumanName = permissionName.split(".").last()
                Toast.makeText(
                    this, "Permission required: $permissionHumanName", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rowPermissionsLocalization = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Location"] != null,
            rowUI = binding.rowPermissionLocalization,
            buttonUI = binding.swPermissionLocalization,
        )
        rowPermissionsBluetooth = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Bluetooth"] != null,
            rowUI = binding.rowPermissionBluetooth,
            buttonUI = binding.swPermissionBluetooth,
        )
        rowPermissionsNotifications = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Notifications"] != null,
            rowUI = binding.rowPermissionNotifications,
            buttonUI = binding.swPermissionNotifications,
        )

        val powerManager = this.getSystemService(POWER_SERVICE) as PowerManager
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(this.packageName)
        rowDisableBatteryOptimization = PermissionsRowAtomicHandler(
            show = !isIgnoringBatteryOptimizations,
            rowUI = binding.rowDisableBatteryOptimization,
            buttonUI = binding.btnDisableBatteryOptimization,
        )

        mapOfRowHandlers = mapOf(
            "Location" to rowPermissionsLocalization,
            "Bluetooth" to rowPermissionsBluetooth,
            "Notifications" to rowPermissionsNotifications,
        )

        for (key in mapOfRowHandlers.keys) {
            val rowHandler = mapOfRowHandlers[key]
            rowHandler?.setOnCheckedChangeListener(
                OnClickListener {
                    promptForPermissions(key)
                })
        }
        rowDisableBatteryOptimization.setOnCheckedChangeListener(
            OnClickListener {
                @SuppressLint("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            })

        binding.btnPermissionsShowInSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri: Uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }

        setSupportActionBar(binding.permissionsToolbar)
        supportActionBar?.title = getString(R.string.permissions_title)
    }

    override fun onResume() {
        super.onResume()
        // Check if all permissions are granted, and exit if so
        exitIfAllPermissionsAreGranted()
        // Iterate over permission rows and disable those that are already granted
        for (key in mapOfRowHandlers.keys) {
            val rowHandler = mapOfRowHandlers[key]
            if (groupPermissionsGranted(this, key)) {
                rowHandler?.disable()
            }
        }
    }

    fun exitIfAllPermissionsAreGranted() {
        // Check if all permissions are granted
        if (allPermissionsGranted(this)) {
            Log.d(TAG, "All granted")
            setResult(RESULT_OK)
            finish()
        }
    }

    fun promptForPermissions(permissionsGroup: String) {
        if (!groupPermissionsGranted(this, permissionsGroup)) {
            val permissions = permissionsByGroupMap[permissionsGroup]

            if (permissions == null) {
                return
            }

            requestPermissionsLauncher.launch(permissions)
        }
    }

    companion object {
        // Main entry point for checking if all permissions are granted
        fun allPermissionsGranted(context: Context): Boolean {
            /** Check if all permissions are granted
             * @param context: Context
             * @return Boolean: True if all permissions are granted, False otherwise
             */
            val permissionsOk = permissionsByGroupMap.keys.all { permissionGroupKey ->
                val granted = groupPermissionsGranted(context, permissionGroupKey)
                Log.i(TAG, "Group $permissionGroupKey granted: $granted")
                granted
            }
            val environmentOk = isIgnoringBatteryOptimizations(context) != false
            Log.i(TAG, "Permissions: $permissionsOk, Environment: $environmentOk")
            return permissionsOk && environmentOk
        }

        private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        fun groupPermissionsGranted(context: Context, groupKey: String): Boolean {
            // Check if all permissions are granted
            val group = permissionsByGroupMap[groupKey]
            val allGranted = group?.all { permission ->
                val granted = permissionGranted(context, permission)
                Log.d(TAG, "Permission $permission granted: $granted")
                granted
            } != false  // if group is null, return true
            Log.d(TAG, "Group $groupKey all permissions granted: $allGranted")
            return allGranted
        }

        /**
         * Determine whether the current [Context] has been granted the relevant [Manifest.permission].
         */
        fun permissionGranted(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        val permissionsByGroupMap: Map<String, Array<String>?> = mapOf(
            "Location" to arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            // BLUETOOTH_CONNECT to obtain additional information
            "Bluetooth" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
            ) else null),
            "Notifications" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(
                Manifest.permission.POST_NOTIFICATIONS
            ) else null),
        )

        val TAG: String = ActPermissions::class.java.name
    }  // companion object
}
