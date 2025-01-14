package com.example.beaconble

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.ComponentCallbacks2
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.beaconble.broadcastReceivers.StopBroadcastReceiver
import com.example.beaconble.ui.ActMain
import com.example.beaconble.workers.SessionFilesUploadWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.RegionViewModel
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import kotlin.concurrent.thread


class AppMain : Application(), ComponentCallbacks2 {
    // API & user services
    private lateinit var apiService: APIService
    lateinit var apiUserSession: ApiUserSession

    // Bluetooth scanning
    // BeaconManager instance
    private val beaconManager: BeaconManager by lazy { BeaconManager.getInstanceForApplication(this) }
    val region = Region(
        "all-beacons",
        null,
        null,
        null
    )  // representa el criterio que se usa para busacar las balizas, como no se quiere buscar una UUID especifica los 3 útlimos campos son null
    private lateinit var regionViewModel: RegionViewModel

    // Beacons abstractions
    var loggingSession = LoggingSession

    // LiveData observers for monitoring and ranging
    // for public use by UI
    lateinit var regionState: MutableLiveData<Int>
    lateinit var rangedBeacons: MutableLiveData<Collection<Beacon>>
    val nRangedBeacons: MutableLiveData<Int> = MutableLiveData(0)

    // Data for the beacon session
    var sessionRunning = MutableLiveData<Boolean>(false)
    val isSessionActive: LiveData<Boolean> get() = sessionRunning

    // Location items
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    // run checkAndNotifyLocationAndBluetoothProviders every second
    // to check if the location and bluetooth providers are enabled
    // and show a notification if they are not
    val checkAndNotifyLocationAndBluetoothProvidersThread = thread(start = false) {
        while (!Thread.interrupted()) {
            if (isSessionActive.value == true) {
                checkAndNotifyLocationAndBluetoothProviders()
                Thread.sleep(1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        regionViewModel = beaconManager.getRegionViewModel(region)
        regionState = regionViewModel.regionState
        rangedBeacons = regionViewModel.rangedBeacons

        // By default, the library will detect AltBeacon protocol beacons
        beaconManager.beaconParsers.removeAll(beaconManager.beaconParsers)
        // m:0-1=0505 stands for InPlay's Company Identifier Code (0x0505), see https://www.bluetooth.com/specifications/assigned-numbers/
        // i:2-7 stands for the identifier, UUID (MAC) [little endian]
        // d:8-9 stands for the data, CH1 analog value [little endian]
        val customParser = BeaconParser().setBeaconLayout("m:0-1=0505,i:2-7,d:8-9")
        beaconManager.beaconParsers.add(customParser)

        // Activate debug mode only if build variant is debug
        BeaconManager.setDebug(BuildConfig.DEBUG)

        // Session initialization
        loggingSession.init(cacheDir)

        // Configure location service and auxiliary items
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configure location request
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            GPS_LOCATION_PERIOD_MILLIS
        ) // Update every second
            .setMinUpdateIntervalMillis(GPS_LOCATION_PERIOD_MILLIS) // Minimum 1 second
            .setWaitForAccurateLocation(true)
            .build()

        //configurar escaneo
        setupBeaconScanning()

        // Set API service
        setupApiService()

        // Save instance for singleton access
        instance = this
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_BACKGROUND) {
            Log.d(TAG, "Trimming memory in background")
            loggingSession.freeDataTemporarily()
        }
    }

    fun setupBeaconScanning() {
        startBeaconScanning()

        // Se llamara al observador cuando se actualice la lista de de beacons (normalmente se actualiza cada 1 seg)
        regionViewModel.rangedBeacons.observeForever(centralRangingObserver)
    }

    //recibe actualizaciones de la lista de beacon detectados y su info
    // hace un calculo del tiempo en millis que ha pasado desde la ultima actualizacion
    val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        val rangeAgeMillis =
            System.currentTimeMillis() - (beacons.firstOrNull()?.lastCycleDetectionTimestamp ?: 0)
        if (rangeAgeMillis < 10000) {
            nRangedBeacons.value = beacons.count()
            // get location latitude and longitude, common for all beacons detected here
            var location: Location? = null
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { loc: Location? ->
                        // Got last known location. In some rare situations this can be null.
                        location = loc
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission denied: $e")
                // TODO handle location permission denied
            }
            // if location is null, set latitude and longitude to NaN
            var latitude = location?.latitude
            var longitude = location?.longitude
            if (location == null) {
                latitude = Double.NaN
                longitude = Double.NaN
            }

            // iterate over beacons and log their data
            val currentInstant = Instant.now()
            for (beacon: Beacon in beacons) {
                val id = beacon.id1
                val data = beacon.dataFields
                // analogReading is the CH1 analog value, as two bytes in little endian
                val analogReading = data[0].toShort()
                addSensorDataEntry(
                    id,
                    analogReading,
                    latitude!!.toFloat(),
                    longitude!!.toFloat(),
                    currentInstant
                )
            }
        }
    }

    fun addSensorDataEntry(
        id: Identifier,
        data: Short,
        latitude: Float,
        longitude: Float,
        timestamp: Instant
    ) {
        loggingSession.addSensorEntry(
            id,
            data,
            latitude,
            longitude,
            timestamp,
        )
    }

    /**
     * Show a notification with the given title and text
     */
    private fun checkAndNotifyLocationAndBluetoothProviders() {
        val locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager
        val bluetoothManager = this.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isBluetoothEnabled = bluetoothManager.adapter.isEnabled

        Log.i(TAG, "GPS enabled: $isGpsEnabled, Bluetooth enabled: $isBluetoothEnabled")
        if (!(isGpsEnabled && isBluetoothEnabled)) {
            showLocationBluetoothNotification(isGpsEnabled, isBluetoothEnabled)
        } else {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID)
        }
    }

    /**
     * Show a extremely important notification for when the GPS or Bluetooth is disabled
     * @param isGpsEnabled: Boolean, true if GPS is enabled
     * @param isBluetoothEnabled: Boolean, true if Bluetooth is enabled
     * @return void
     */
    private fun showLocationBluetoothNotification(
        isGpsEnabled: Boolean,
        isBluetoothEnabled: Boolean
    ) {
        var notificationMessageText = ""
        if (!isGpsEnabled && !isBluetoothEnabled) {
            notificationMessageText = getString(R.string.notification_no_location_bluetooth_text)
        } else if (!isGpsEnabled) {
            notificationMessageText = getString(R.string.notification_no_location_text)
        } else if (!isBluetoothEnabled) {
            notificationMessageText = getString(R.string.notification_no_bluetooth_text)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, "vipv-app-location-bluetooth")
            .setContentTitle(getString(R.string.notification_no_location_bluetooth_title))
            .setContentText(notificationMessageText)
            .setSmallIcon(R.mipmap.logo_ies_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(  // Explicit intent to open the app when notification is clicked
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, ActMain::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

        val channel = NotificationChannel(
            "vipv-app-location-bluetooth",  // id
            getString(R.string.notification_no_location_bluetooth_channel_name),  // name
            NotificationManager.IMPORTANCE_HIGH,  // importance
        )
        channel.description = "Notifies when location or Bluetooth are disabled."
        notificationManager.createNotificationChannel(channel)
        notificationManager.notify(NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID, builder.build())
    }

    /**
     * Show a notification when a session is ongoing
     */
    private fun showSessionOngoingNotification() {
        // intents for stopping the session
        val stopIntent = Intent(this, StopBroadcastReceiver::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // configure notification
        val builder = NotificationCompat.Builder(this, "vipv-app-session-ongoing")
            .setContentTitle(getString(R.string.notification_ongoing_title))
            .setContentText(getString(R.string.notification_ongoing_text))
            .setSmallIcon(R.mipmap.logo_ies_foreground)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(  // Stop action
                R.drawable.square_stop,
                getString(R.string.stop_notification_button),
                stopPendingIntent
            )
            .setContentIntent(  // Explicit intent to open the app when notification is clicked
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, ActMain::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

        val channel = NotificationChannel(
            "vipv-app-session-ongoing",  // id
            getString(R.string.notification_ongoing_channel_name),  // name
            NotificationManager.IMPORTANCE_HIGH,  // importance
        )
        channel.description = "Notifies when a measurement session is active."
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        notificationManager.notify(NOTIFICATION_ONGOING_SESSION_ID, builder.build())
    }

    /**
     * Setup the API service endpoint (callback for configuration changes)
     * If the endpoint is not set in PreferenceManager.getDefaultSharedPreferences,
     * the default value is used (from BuildConfig)
     * @param newUri: String, new URI to set for the API service if known, else it is read from shared preferences
     * @return void
     */
    fun setupApiService(newUri: String? = null) {
        if (newUri.isNullOrBlank()) {
            // Get the API URI setting
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            var endpoint =
                sharedPreferences.getString("api_uri", BuildConfig.SERVER_URL) ?: BuildConfig.SERVER_URL
            if (endpoint.isBlank()) {
                endpoint = BuildConfig.SERVER_URL
            }
            setupApiService(endpoint)
            return
        }
        var endpoint = newUri
        if (!endpoint.endsWith("/")) {
            endpoint += "/"
        }
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "http://${endpoint}"
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(endpoint)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        this.apiService = retrofit.create(APIService::class.java)
        // Load user session from shared preferences
        apiUserSession = ApiUserSession(PreferenceManager.getDefaultSharedPreferences(this), apiService)
    }

    /**
     * Test the API endpoint by sending a GET request to the server
     */
    suspend fun testApiEndpoint(): Boolean {
        try {
            val response = apiService.isUp()
            Log.i(TAG, "API is up: $response")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "API is down: $e")
            return false
        }
    }

    /**
     * Stop monitoring and ranging for beacons
     * @return void
     */
    fun stopBeaconScanning() {
        loggingSession.stopInstant = Instant.now()
        beaconManager.stopMonitoring(region)
        beaconManager.stopRangingBeacons(region)
        sessionRunning.value = false

        // Remove notification(s)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ONGOING_SESSION_ID)
        notificationManager.cancel(NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID)

        // Create a coroutine to write the session data to a file
        thread {
            loggingSession.concludeSession()
        }
    }

    /**
     * Start monitoring and ranging for beacons
     * @return void
     */
    fun startBeaconScanning() {
        loggingSession.startInstant = Instant.now()
        beaconManager.startMonitoring(region)
        beaconManager.startRangingBeacons(region)
        sessionRunning.value = true

        // Start the thread to check and notify location and bluetooth providers
        if (!checkAndNotifyLocationAndBluetoothProvidersThread.isAlive) {
            checkAndNotifyLocationAndBluetoothProvidersThread.start()
        }

        // Start location updates
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            checkAndNotifyLocationAndBluetoothProviders()
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                {
                    // Do nothing, just request location updates
                },
                null
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied: $e")
            // TODO handle location permission denied
        }

        // Configure the notification channel and send the notification
        showSessionOngoingNotification()
    }

    /**
     * Toggle the beacon scanning session
     * @return void
     *
     * Observe sessionRunning LiveData to get the current state of the session
     */
    fun toggleSession() {
        if (sessionRunning.value == true) {
            stopBeaconScanning()
        } else {
            startBeaconScanning()
        }
    }

    fun emptyAll() {
        loggingSession.emptyAll()
    }

    fun concludeSession() {
        stopBeaconScanning()
        loggingSession.concludeSession()
        // if the sharedPreference is set to upload files on metered network, schedule the upload
        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("auto_upload_on_metered", true)
        ) {
            scheduleFileUpload()
        }
    }

    fun scheduleFileUpload() {
        Log.i(TAG, "Scheduling file upload")
        // Define constraints for unmetered network
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        // Create a OneTimeWorkRequest
        val fileUploadWorkRequest = OneTimeWorkRequestBuilder<SessionFilesUploadWorker>()
            .setConstraints(constraints)
            .build()

        // Enqueue the work
        WorkManager.getInstance(this).enqueue(fileUploadWorkRequest)
    }

    fun uploadAll() {
        // Create a OneTimeWorkRequest
        val fileUploadWorkRequest = OneTimeWorkRequestBuilder<SessionFilesUploadWorker>()
            .build()
        // Enqueue the work
        WorkManager.getInstance(this).enqueue(fileUploadWorkRequest)
    }

    override fun onTerminate() {
        stopBeaconScanning()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: AppMain
            private set  // This is a singleton, setter is private but access is public
        const val TAG = "AppMain"
        const val NOTIFICATION_ONGOING_SESSION_ID = 1
        const val NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID = 2
        const val ACTION_STOP_SESSION = "com.example.beaconble.STOP_SESSION"
        const val GPS_LOCATION_PERIOD_MILLIS = 1000L
    }  // companion object
}
