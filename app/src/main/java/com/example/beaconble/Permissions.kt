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
import android.widget.Button
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TableRow


class PermissionsRowAtomicHandler(
    val show: Boolean,
    val rowUI: TableRow,
    val switchUI: SwitchMaterial,
    // val clikedPermissionsCallback: (permissions: Array<String>) -> Unit,
) {
    // Group of permissions that are related to a single UI element
    // Ease access, management and UI interaction
    // manifestPermissions: Array of permissions that are related to the UI element (will depend on the target version)
    // rowUI: TableRow that contains the UI element
    // switchUI: SwitchMaterial that is the UI element

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
        // Disable the switch element
        switchUI.isEnabled = false
    }
    fun setOnCheckedChangeListener(clickedCallback: CompoundButton.OnCheckedChangeListener) {
        // Set the callback for the switch
        switchUI.setOnCheckedChangeListener(clickedCallback)
    }
}


open class PermissionsActivity: AppCompatActivity() {
    //comprueba el resultado de todos los permisos mediante una Actividad y un Contract

    val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Es un mapa Map<String, Boolean> donde String=Key permiso y Boolean=resultado
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted == true) {
                    Log.d(TAG, "$permissionName permission granted: $isGranted")

                } else {
                    Log.d(TAG, "$permissionName permission granted: $isGranted")
                    // Mostrar mensaje que se ha rechazado el permiso
                }
            }
        }
    companion object {
        //Al ser Log.d no afectan al usuario solo informativo del Debug
        const val TAG = "PermissionsActivity"
    }
}


open class BeaconScanPermissionsActivity: PermissionsActivity()  {
    lateinit var sysSettingsButton: Button

    lateinit var rowPermissionsLocalization: PermissionsRowAtomicHandler
    lateinit var rowPermissionsLocalizationInBackground: PermissionsRowAtomicHandler
    lateinit var rowPermissionsBluetooth: PermissionsRowAtomicHandler
    lateinit var rowPermissionsNotifications: PermissionsRowAtomicHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        //hay que ejecutar el codigo de la AppCompatActivity padre antes que el de esta clase
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_permissions)

        rowPermissionsLocalization = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Location"]!=null,
            rowUI = findViewById<TableRow>(R.id.row_permission_localization),
            switchUI = findViewById<SwitchMaterial>(R.id.sw_permission_localization),
        )
        rowPermissionsLocalization.setOnCheckedChangeListener(
            CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                promptForPermissions("Location")
                val successful = groupPermissionsGranted(this, "Location")
                if (successful) {
                    rowPermissionsLocalization.disable()
                    callbackToContinueIfAllPermissionsAreGranted()
                }
            }
        })
        rowPermissionsLocalizationInBackground = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Location in Background"]!=null,
            rowUI = findViewById<TableRow>(R.id.row_permission_localization_background),
            switchUI = findViewById<SwitchMaterial>(R.id.sw_permission_localization_background),
        )
        rowPermissionsLocalizationInBackground.setOnCheckedChangeListener(
            CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    promptForPermissions("Location in Background")
                    val successful = groupPermissionsGranted(this, "Location in Background")
                    if (successful) {
                        rowPermissionsLocalizationInBackground.disable()
                        callbackToContinueIfAllPermissionsAreGranted()
                    }
                }
            }
        )
        rowPermissionsBluetooth = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Bluetooth"]!=null,
            rowUI = findViewById<TableRow>(R.id.row_permission_bluetooth),
            switchUI = findViewById<SwitchMaterial>(R.id.sw_permission_bluetooth),
        )
        rowPermissionsBluetooth.setOnCheckedChangeListener(
            CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    promptForPermissions("Bluetooth")
                    val successful = groupPermissionsGranted(this, "Bluetooth")
                    if (successful) {
                        rowPermissionsBluetooth.disable()
                        callbackToContinueIfAllPermissionsAreGranted()
                    }
                }
            }
        )
        rowPermissionsNotifications = PermissionsRowAtomicHandler(
            show = permissionsByGroupMap["Notifications"]!=null,
            rowUI = findViewById<TableRow>(R.id.row_permission_notifications),
            switchUI = findViewById<SwitchMaterial>(R.id.sw_permission_notifications),
        )
        rowPermissionsNotifications.setOnCheckedChangeListener(
            CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    promptForPermissions("Notifications")
                    val successful = groupPermissionsGranted(this, "Notifications")
                    if (successful) {
                        rowPermissionsNotifications.disable()
                        callbackToContinueIfAllPermissionsAreGranted()
                    }
                }
            }
        )

        sysSettingsButton = findViewById<Button>(R.id.btn_permissions_show_in_settings)
        sysSettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri: Uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
    }

    fun callbackToContinueIfAllPermissionsAreGranted() {
        // Check if all permissions are granted
        if (allPermissionsGranted(this)) {
            Log.d(TAG, "All granted")
            setResult(RESULT_OK)
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
        }
    }
