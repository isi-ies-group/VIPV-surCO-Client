package com.example.beaconble

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View.OnClickListener
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import android.widget.TableRow
import android.widget.Toast
import androidx.appcompat.widget.Toolbar


class PermissionsRowAtomicHandler(
    val show: Boolean,
    val rowUI: TableRow,
    val buttonUI: MaterialButton,
    // val clikedPermissionsCallback: (permissions: Array<String>) -> Unit,
) {
    // Group of permissions that are related to a single UI element
    // Ease access, management and UI interaction
    // manifestPermissions: Array of permissions that are related to the UI element (will depend on the target version)
    // rowUI: TableRow that contains the UI element
    // buttonUI: MaterialButton that is the UI element

    init {
        if (show == false) {
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


open class BeaconScanPermissionsActivity: AppCompatActivity() {
    lateinit var sysSettingsButton: Button

    lateinit var rowPermissionsLocalization: PermissionsRowAtomicHandler
    lateinit var rowPermissionsLocalizationInBackground: PermissionsRowAtomicHandler
    lateinit var rowPermissionsBluetooth: PermissionsRowAtomicHandler
    lateinit var rowPermissionsNotifications: PermissionsRowAtomicHandler

    lateinit var mapOfRowHandlers: Map<String, PermissionsRowAtomicHandler>
    lateinit var toolbar: Toolbar

    val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Es un mapa Map<String, Boolean> donde String=Key permiso y Boolean=resultado
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                Log.d(TAG, "$permissionName permission granted: $isGranted")
                if (!isGranted) {
                    // Mostrar mensaje que se ha rechazado el permiso
                    Toast.makeText(
                        this,
                        "Permission $permissionName is required to continue",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        //hay que ejecutar el codigo de la AppCompatActivity padre antes que el de esta clase
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        rowPermissionsLocalization = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Location"]!=null,
            rowUI = findViewById<TableRow>(R.id.row_permission_localization),
            buttonUI = findViewById<MaterialButton>(R.id.sw_permission_localization),
        )
        rowPermissionsLocalizationInBackground = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Location in Background"]!=null,
            rowUI = findViewById<TableRow>(R.id.row_permission_localization_background),
            buttonUI = findViewById<MaterialButton>(R.id.sw_permission_localization_background),
        )
        rowPermissionsBluetooth = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Bluetooth"]!=null,
            rowUI = findViewById<TableRow>(R.id.row_permission_bluetooth),
            buttonUI = findViewById<MaterialButton>(R.id.sw_permission_bluetooth),
        )
        rowPermissionsNotifications = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Notifications"]!=null,
            rowUI = findViewById<TableRow>(R.id.row_permission_notifications),
            buttonUI = findViewById<MaterialButton>(R.id.sw_permission_notifications),
        )

        mapOfRowHandlers = mapOf(
            "Location" to rowPermissionsLocalization,
            "Location in Background" to rowPermissionsLocalizationInBackground,
            "Bluetooth" to rowPermissionsBluetooth,
            "Notifications" to rowPermissionsNotifications,
        )

        for (key in mapOfRowHandlers.keys) {
            val rowHandler = mapOfRowHandlers[key]
            rowHandler?.setOnCheckedChangeListener(
                OnClickListener {
                    promptForPermissions(key)
                }
            )
        }

        sysSettingsButton = findViewById<Button>(R.id.btn_permissions_show_in_settings)
        sysSettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri: Uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }

        toolbar = findViewById<Toolbar>(R.id.permissions_toolbar)
        setSupportActionBar(toolbar)
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

    @SuppressLint("ObsoleteSdkInt")
    fun promptForPermissions(permissionsGroup: String) {
        if (!groupPermissionsGranted(this, permissionsGroup)) {
            val permissions = permissionsByGroupMap[permissionsGroup]

            if (permissions == null) {
                return
            }

//            val showRationale = permissions.any {
//                shouldShowRequestPermissionRationale(it)
//            }

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
            return permissionsByGroupMap.keys.none { permissionGroup ->
                groupPermissionsGranted(context, permissionGroup) == false
            }
        }

        fun groupPermissionsGranted(context: Context, groupKey: String): Boolean {
            // Check if all permissions are granted
            val group = permissionsByGroupMap[groupKey]
            if (group == null) {
                return true
            }
            else {
                for (permission in group) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            permission
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return false
                    }
                }
                return true
            }
        }

        @SuppressLint("ObsoleteSdkInt")
        val permissionsByGroupMap: Map<String, Array<String>?> = mapOf(
                "Location" to (if (Build.VERSION.SDK_INT >= 1) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) else null),
                "Location in Background" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else null),
                // BLUETOOTH_CONNECT to obtain additional information
                "Bluetooth" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else null),
                "Notifications" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else null),
            )

        const val TAG = "PermissionsActivity"
        }  // companion object
    }
