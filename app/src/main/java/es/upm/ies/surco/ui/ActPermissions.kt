package es.upm.ies.surco.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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
        isIgnoringBatteryOptimizations =
            powerManager.isIgnoringBatteryOptimizations(this.packageName)
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
                try {
                    @SuppressLint("BatteryLife") val intent =
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    try {
                        val intent =
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                }
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
            setResult(RESULT_OK)
            finish()
        }
    }

    fun promptForPermissions(permissionsGroup: String) {
        Log.d(TAG, "Prompting for permissions group: $permissionsGroup")
        if (!groupPermissionsGranted(this, permissionsGroup)) {
            val permissions = permissionsByGroupMap[permissionsGroup]

            if (permissions == null) {
                return
            }

            requestPermissionsLauncher.launch(permissions)
        }
    }

    companion object {
        private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        fun groupPermissionsGranted(context: Context, groupKey: String): Boolean {
            // Check if all permissions are granted
            val group = permissionsByGroupMap[groupKey]
            val allGranted = group?.all { permission ->
                val granted = permissionGranted(context, permission)
                granted
            } != false  // if group is null, return true
            return allGranted
        }

        /**
         * Determine whether the current [Context] has been granted the relevant [Manifest.permission].
         */
        fun permissionGranted(context: Context, permission: String): Boolean {
            Log.d(TAG, "Checking permission: $permission")
            val result = ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission granted: $result")
            return result
        }

        // Update permissions map to handle version differences
        val permissionsByGroupMap: Map<String, Array<String>?> = mapOf(
            "Location" to when {
                Build.VERSION.SDK_INT >= 29 /* Android 10+ */ -> arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )

                else -> arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }, "Bluetooth" to (if (Build.VERSION.SDK_INT >= 31 /* Android 12+ */) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN)
            } else /* Android 11- */ arrayOf(
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN
            )), "Notifications" to when {
                Build.VERSION.SDK_INT >= 33 /* Android 13+ */ -> arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                )

                else -> null
            }, "ForegroundService" to when {
                Build.VERSION.SDK_INT >= 34 /* Android 14+ */ -> arrayOf( /* Android 14+ */
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION
                )

                Build.VERSION.SDK_INT >= 28 -> arrayOf(
                    /* Android 9+ */
                    Manifest.permission.FOREGROUND_SERVICE,
                )

                else -> null
            }
        )

        // Updated allPermissionsGranted method
        fun allPermissionsGranted(context: Context): Boolean {
            Log.d(TAG, "Checking all permissions...")
            val permissionsOk = permissionsByGroupMap.keys.all { permissionGroupKey ->
                val result = groupPermissionsGranted(context, permissionGroupKey)
                Log.d(TAG, "Checked group: $permissionGroupKey -> $result")
                result
            }
            val foregroundOk = groupPermissionsGranted(context, "ForegroundService")
            val environmentOk = isIgnoringBatteryOptimizations(context) != false
            Log.d(
                TAG,
                "Permissions OK: $permissionsOk, Foreground Service OK: $foregroundOk, Battery Optimization Ignored: $environmentOk"
            )

            val final = permissionsOk && foregroundOk && environmentOk
            Log.d(TAG, "All permissions granted: $final")
            return final
        }

        val TAG: String = ActPermissions::class.java.simpleName
    }
}
