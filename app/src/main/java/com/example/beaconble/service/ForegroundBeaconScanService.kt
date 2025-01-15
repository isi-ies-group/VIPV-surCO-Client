package com.example.beaconble.service

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.beaconble.AppMain
import com.example.beaconble.AppMain.Companion.ACTION_STOP_SESSION
import com.example.beaconble.R
import com.example.beaconble.broadcastReceivers.StopBroadcastReceiver
import com.example.beaconble.ui.ActMain
import com.google.android.gms.location.*
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.service.BeaconService
import java.time.Instant

// TODO: implement onLowMemory and onTrimMemory
class ForegroundBeaconScanService : BeaconService() {
    private lateinit var appMain: AppMain

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    val region = Region(
        "all-beacons", null, null, null
    )  // criteria for identifying beacons

    val rangingCallback: org.altbeacon.beacon.service.Callback =
        object : org.altbeacon.beacon.service.Callback("com.example.beaconble") {
            override fun call(context: Context?, dataName: String?, data: Bundle?): Boolean {
                Log.i(TAG, "Ranging callback called with dataName: $dataName")
                Log.i(TAG, "Data: $data")
                // get the beacons from the bundle
                val beacons = data?.getParcelableArrayList<Beacon>("beacons") ?: return false
                // get the current location
                var location: Location? = null
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                        // Got last known location. In some rare situations this can be null.
                        location = loc
                    }
                } catch (e: SecurityException) {
                    Log.e(AppMain.Companion.TAG, "Location permission denied: $e")
                    // TODO handle location permission denied
                }
                // add the beacons to the appMain
                appMain.addSensorDataEntry(beacons, location, Instant.now())
                return true
            }
        }
    val monitoringCallback: org.altbeacon.beacon.service.Callback =
        object : org.altbeacon.beacon.service.Callback("com.example.beaconble") {
            override fun call(context: Context?, dataName: String?, data: Bundle?): Boolean {
                Log.i(TAG, "Monitoring callback called with dataName: $dataName")
                return true
            }
        }

    override fun onCreate() {
        super.onCreate()

        // get AppMain singleton
        appMain = AppMain.instance

        // Initialize the location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configure the location request
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, AppMain.GPS_LOCATION_PERIOD_MILLIS
        ).setMinUpdateIntervalMillis(AppMain.GPS_LOCATION_PERIOD_MILLIS)
            .setWaitForAccurateLocation(true).build()

        // Setup location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d(
                    TAG,
                    "Location: ${locationResult.lastLocation?.latitude}, ${locationResult.lastLocation?.longitude}"
                )
            }
        }

        // Create all notification channels
        createNotificationChannels()

        // Start monitoring Bluetooth and GPS connectivity
        startForegroundServiceWithNotification()
        startLocationUpdates()
        enabledBluetoothAndGpsWatchdog()

        // Start ranging and monitoring beacons
        startRangingBeaconsInRegion(region, rangingCallback)
        startMonitoringBeaconsInRegion(region, monitoringCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Create all notification channels used by the service.
     */
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Connectivity Service Channel
        val ongoingSessionChannel = NotificationChannel(
            "session-ongoing",  // id
            getString(R.string.notification_ongoing_channel_name),  // name
            NotificationManager.IMPORTANCE_HIGH,  // importance
        ).apply {
            description = getString(R.string.notification_ongoing_channel_description)
        }
        notificationManager.createNotificationChannel(ongoingSessionChannel)

        // Location and Bluetooth Watchdog Channel
        val watchdogChannel = NotificationChannel(
            "location-and-bluetooth-watchdog",  // id
            getString(R.string.notification_watchdog_channel_name),  // name
            NotificationManager.IMPORTANCE_HIGH  // importance
        ).apply {
            description = getString(R.string.notification_watchdog_channel_description)
        }
        notificationManager.createNotificationChannel(watchdogChannel)
    }

    /**
     * Start the service in the foreground with a notification.
     */
    private fun startForegroundServiceWithNotification() {
        // intents for stopping the session
        val stopIntent = Intent(this, StopBroadcastReceiver::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Create the notification
        val notification = NotificationCompat.Builder(this, "session-ongoing")
            .setContentTitle(getString(R.string.notification_ongoing_title))
            .setContentText(getString(R.string.notification_ongoing_text))
            .setSmallIcon(R.mipmap.logo_ies_foreground).setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).addAction(  // Stop action
                R.drawable.square_stop,
                getString(R.string.stop_notification_button),
                stopPendingIntent
            ).setContentIntent(  // Explicit intent to open the app when notification is clicked
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, ActMain::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ).build()

        // Start the service in the foreground
        startForeground(AppMain.NOTIFICATION_ONGOING_SESSION_ID, notification)
    }

    /**
     * Start location updates. Allows to retrieve the user's location periodically.
     */
    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, null
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied: $e")
        }
    }

    /**
     * Stop location updates.
     */
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    /**
     * Continuously check if Bluetooth and GPS are enabled.
     * If not, show a notification to the user.
     */
    private fun enabledBluetoothAndGpsWatchdog() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        Thread {
            while (true) {
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isBluetoothEnabled = bluetoothManager.adapter.isEnabled

                Log.i(TAG, "GPS enabled: $isGpsEnabled, Bluetooth enabled: $isBluetoothEnabled")

                if (!isGpsEnabled || !isBluetoothEnabled) {
                    showConnectivityNotification(isGpsEnabled, isBluetoothEnabled)
                } else {
                    val notificationManager =
                        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(AppMain.NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID)
                }

                Thread.sleep(2500)
            }
        }.start()
    }

    /**
     * Show a notification to the user if GPS or Bluetooth are disabled.
     * @param isGpsEnabled True if GPS is enabled, false otherwise.
     * @param isBluetoothEnabled True if Bluetooth is enabled, false otherwise.
     * @see enabledBluetoothAndGpsWatchdog
     */
    private fun showConnectivityNotification(isGpsEnabled: Boolean, isBluetoothEnabled: Boolean) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationText = when {
            !isGpsEnabled && !isBluetoothEnabled -> getString(R.string.notification_no_location_bluetooth_text)
            !isGpsEnabled -> getString(R.string.notification_no_location_text)
            !isBluetoothEnabled -> getString(R.string.notification_no_bluetooth_text)
            else -> ""
        }

        val notification = NotificationCompat.Builder(this, "location-and-bluetooth-watchdog")
            .setContentTitle(getString(R.string.notification_no_location_bluetooth_title))
            .setContentText(notificationText).setSmallIcon(R.mipmap.logo_ies_foreground)
            .setOngoing(true).build()

        notificationManager.notify(AppMain.NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID, notification)
    }

    companion object {
        private const val TAG = "ForegroundBeaconScanService"
    }
}
