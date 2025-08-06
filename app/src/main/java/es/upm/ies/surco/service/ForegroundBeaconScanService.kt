package es.upm.ies.surco.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import es.upm.ies.surco.AppMain
import es.upm.ies.surco.AppMain.Companion.ACTION_STOP_SESSION
import es.upm.ies.surco.R
import es.upm.ies.surco.broadcastReceivers.StopBroadcastReceiver
import es.upm.ies.surco.ui.ActMain
import java.time.Instant
import kotlin.concurrent.thread


class ForegroundBeaconScanService : Service(), SensorEventListener {
    private lateinit var appMain: AppMain

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var locationManager: LocationManager

    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var scanCallback: ScanCallback

    val scanFilters = listOf(
        ScanFilter.Builder().setManufacturerData(0x0505, "ies.upm.es".encodeToByteArray()).build()
    )

    val scanSettings: ScanSettings =
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    /**
     * Latitude and longitude of the user's location.
     */
    private var latitude: Float = Float.NaN
    private var longitude: Float = Float.NaN

    /**
     * Flag to indicate if the watchdog thread is running.
     */
    @Volatile
    private var watchdogRunning = false

    /**
     * Watchdog thread that continuously checks if Bluetooth and GPS are enabled.
     */
    private val watchdogThread = thread(start = false) {
        while (watchdogRunning) {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isBluetoothEnabled = bluetoothManager.adapter.isEnabled

            handleConnectivityNotification(isGpsEnabled, isBluetoothEnabled)

            try {
                Thread.sleep(2500)
            } catch (_: InterruptedException) {
                break  // Gracefully exit the thread if interrupted, we already have enough drama in this world
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Service created")

        // get AppMain singleton
        appMain = applicationContext as AppMain

        // Get the Bluetooth and Location managers
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

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
                // save latitude and longitude for later use on data entries addition
                latitude = locationResult.lastLocation?.latitude?.toFloat() ?: Float.NaN
                longitude = locationResult.lastLocation?.longitude?.toFloat() ?: Float.NaN
            }
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    val timestamp = Instant.now()
                    val compassAzimuth = getCompassAzimuth()
                    val scanRecord = it.scanRecord

                    val address = it.device.address

                    // AltBeacon: val customParser = BeaconParser().setBeaconLayout("m:0-1=0505,i:2-7,d:8-9")
                    val manufacturerData = scanRecord?.getManufacturerSpecificData(0x0505)
                    // See beacon config v3
                    val value = manufacturerData?.takeIf { it.size == 12 }?.copyOfRange(10, 12)
                    if (address != null && value != null) {
                        val valueShort =  // value is little-endian, convert to short
                            (value[1].toInt() shl 8 or (value[0].toInt() and 0xFF)).toShort()
                        appMain.addSensorDataEntry(
                            timestamp,
                            address,
                            valueShort,
                            latitude,
                            longitude,
                            compassAzimuth
                        )
                    } else {
                        Log.w(
                            TAG,
                            "Manufacturer data is not valid or does not contain enough information"
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error: $errorCode")
            }
        }


        // Create all notification channels
        createNotificationChannels()

        // Register listeners for sensors
        startSensorUpdates()

        // Start monitoring Bluetooth and GPS connectivity
        startForegroundServiceWithNotification()
        startLocationUpdates()
        startBluetoothAndGpsWatchdog()

        // Start ranging and monitoring beacons
        bluetoothLeScanner = bluetoothManager.adapter.bluetoothLeScanner
        // Check permissions before starting the scan
        if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Bluetooth scan start error: permission not granted")
        }
        bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensorUpdates()
        stopBluetoothAndGpsWatchdog()
        stopLocationUpdates()
        // Stop ranging and monitoring beacons
        if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeScanner.stopScan(scanCallback)
        } else {
            Log.e(TAG, "Bluetooth scan stop error: permission not granted")
        }
        // Remove notifications
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(AppMain.NOTIFICATION_ONGOING_SESSION_ID)
        notificationManager.cancel(AppMain.NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID)
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
        val notification = NotificationCompat.Builder(this, "session-ongoing").apply {
            setContentTitle(getString(R.string.notification_ongoing_title))
            setContentText(getString(R.string.notification_ongoing_text))
            setSmallIcon(R.mipmap.logo_surco).setOngoing(true)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC).addAction(  // Stop action
                R.drawable.square_stop,
                getString(R.string.stop_notification_button),
                stopPendingIntent
            )
        }.setContentIntent(  // Explicit intent to open the app when notification is clicked
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
    private fun startBluetoothAndGpsWatchdog() {
        if (!watchdogRunning) {
            watchdogRunning = true
            watchdogThread.start()
        }
    }

    /**
     * Stop the watchdog thread.
     */
    private fun stopBluetoothAndGpsWatchdog() {
        watchdogRunning = false
        watchdogThread.join() // Ensure the thread stops before continuing
    }

    private var lastNotificationState: Pair<Boolean, Boolean> = Pair(false, false)

    /**
     * Show a notification to the user if GPS or Bluetooth are disabled.
     * @param isGpsEnabled True if GPS is enabled, false otherwise.
     * @param isBluetoothEnabled True if Bluetooth is enabled, false otherwise.
     * @see startBluetoothAndGpsWatchdog
     */
    private fun handleConnectivityNotification(isGpsEnabled: Boolean, isBluetoothEnabled: Boolean) {
        val currentState = Pair(isGpsEnabled, isBluetoothEnabled)
        if (currentState == lastNotificationState) return  // No change -> do not create a new notification
        if (currentState == Pair(true, true)) {  // Both are enabled -> remove notification
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(AppMain.NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID)
            return
        }

        // Create a notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationText = when {
            !isGpsEnabled && !isBluetoothEnabled -> getString(R.string.notification_no_location_bluetooth_text)
            !isGpsEnabled -> getString(R.string.notification_no_location_text)
            !isBluetoothEnabled -> getString(R.string.notification_no_bluetooth_text)
            else -> ""
        }

        val notification =
            NotificationCompat.Builder(this, "location-and-bluetooth-watchdog").apply {
                setContentTitle(getString(R.string.notification_no_location_bluetooth_title))
                setContentText(notificationText).setSmallIcon(R.mipmap.logo_surco)
                setOngoing(true)
            }.build()

        notificationManager.notify(AppMain.NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID, notification)

        lastNotificationState = currentState
    }


    /**
     * Get compass azimuth angle in degrees.
     */
    private val sensorManager: SensorManager by lazy {
        getSystemService(SENSOR_SERVICE) as SensorManager
    }
    private val accelerometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    private val magnetometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }
    private val hasCompass: Boolean by lazy {
        accelerometer != null && magnetometer != null
    }
    private var magnetometerAccuracyStatus: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var accelerometerAccuracyStatus: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val lastAccelerometerReading = FloatArray(3)
    private val lastMagnetometerReading = FloatArray(3)
    fun getCompassAzimuth(): Float {
        if (!hasCompass) return Float.NaN

        // get last reading of accelerometer and magnetometer
        SensorManager.getRotationMatrix(
            rotationMatrix, null, lastAccelerometerReading, lastMagnetometerReading
        )
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val azimuthInRadians = orientationAngles[0]
        return Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
    }

    /**
     * Called when the sensor values change.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Update the last accelerometer reading
                System.arraycopy(
                    event.values, 0, lastAccelerometerReading, 0, lastAccelerometerReading.size
                )
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Update the last magnetometer reading
                System.arraycopy(
                    event.values, 0, lastMagnetometerReading, 0, lastMagnetometerReading.size
                )
            }
        }
    }

    /**
     * Called when the sensor accuracy changes.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetometerAccuracyStatus = accuracy
        } else if (sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            accelerometerAccuracyStatus = accuracy
        }
        appMain.minSensorAccuracy.postValue(
            minOf(
                magnetometerAccuracyStatus, accelerometerAccuracyStatus
            )
        )
    }

    /**
     * Start sensor updates.
     */
    fun startSensorUpdates() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Stop sensor updates.
     */
    fun stopSensorUpdates() {
        sensorManager.unregisterListener(this)
    }

    companion object {
        private const val TAG = "ForegroundBeaconScanService"
    }
}
