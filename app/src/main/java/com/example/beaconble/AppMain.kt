package com.example.beaconble

import android.app.*
import android.content.Intent
import android.content.ComponentCallbacks2
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.beaconble.service.ForegroundBeaconScanService
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
import org.apache.commons.collections4.queue.CircularFifoQueue


class AppMain : Application(), ComponentCallbacks2 {
    // API & user services
    private lateinit var apiService: APIService
    lateinit var apiUserSession: ApiUserSession

    // Bluetooth scanning
    // BeaconManager instance
    private val beaconManager: BeaconManager by lazy { BeaconManager.getInstanceForApplication(this) }
    val region = Region(
        "all-beacons", null, null, null
    )  // criteria for identifying beacons
    private lateinit var regionViewModel: RegionViewModel

    // Ring buffer to store the last 5 numbers of beacons detected,
    // to avoid reporting a lower number by mis-skipping them in scans
    private val nRangedBeaconsBuffer = CircularFifoQueue<Int>(5)

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

    override fun onCreate() {
        super.onCreate()

        regionViewModel = beaconManager.getRegionViewModel(region)
        regionState = regionViewModel.regionState
        rangedBeacons = regionViewModel.rangedBeacons

        // By default, the library will detect AltBeacon protocol beacons
        beaconManager.beaconParsers.removeAll(beaconManager.beaconParsers)
        // m:0-1=0505 stands for InPlay's Company Identifier Code (0x0505),
        // see https://www.bluetooth.com/specifications/assigned-numbers/
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
            Priority.PRIORITY_HIGH_ACCURACY, GPS_LOCATION_PERIOD_MILLIS
        ) // Update every second
            .setMinUpdateIntervalMillis(GPS_LOCATION_PERIOD_MILLIS) // Minimum 1 second
            .setWaitForAccurateLocation(true).build()

        setupBeaconScanning()

        // Set API service
        setupApiService()

        // Save instance for singleton access
        instance = this
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        loggingSession.freeDataTemporarily()
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
    }

    /**
     * Add sensor data entries to the loggingSession from the beacon data returned by the ranging
     * @param beacons: Collection<Beacon>, list of beacons detected by the ranging
     * @param location: Location, location of the data
     * @param timestamp: Instant, timestamp of the data
     */
    fun addBeaconCollectionData(
        beacons: Collection<Beacon>, location: Location?, timestamp: Instant
    ) {
        // if location is null, set latitude and longitude to NaN
        val latitude = location?.latitude?.toFloat() ?: Float.NaN
        val longitude = location?.longitude?.toFloat() ?: Float.NaN

        for (beacon in beacons) {
            val id = beacon.id1
            val data = beacon.dataFields
            // analogReading is the CH1 analog value, as two bytes in little endian
            val analogReading = data[0].toShort()
            addSensorDataEntry(id, analogReading, latitude, longitude, timestamp)
        }

        // Update the number of beacons detected
        nRangedBeaconsBuffer.add(beacons.size)
        nRangedBeacons.value = nRangedBeaconsBuffer.maxOrNull() ?: 0
    }

    /**
     * Add sensor data entry to the loggingSession
     * @param id: Identifier, identifier of the beacon
     * @param data: Short, data to be added to the beacon
     * @param latitude: Float, latitude of the beacon
     * @param longitude: Float, longitude of the beacon
     * @param timestamp: Instant, timestamp of the data
     */
    fun addSensorDataEntry(
        id: Identifier, data: Short, latitude: Float, longitude: Float, timestamp: Instant
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
            var endpoint = sharedPreferences.getString("api_uri", BuildConfig.SERVER_URL)
                ?: BuildConfig.SERVER_URL
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
        val retrofit =
            Retrofit.Builder().baseUrl(endpoint).addConverterFactory(GsonConverterFactory.create())
                .build()
        this.apiService = retrofit.create(APIService::class.java)
        // Load user session from shared preferences
        apiUserSession =
            ApiUserSession(PreferenceManager.getDefaultSharedPreferences(this), apiService)
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

        // Create a coroutine to write the session data to a file
        thread {
            loggingSession.concludeSession()
        }

        val serviceIntent = Intent(this, ForegroundBeaconScanService::class.java)
        stopService(serviceIntent)
    }

    /**
     * Start monitoring and ranging for beacons
     * @return void
     */
    fun startBeaconScanning() {
        loggingSession.clear()
        loggingSession.startInstant = Instant.now()
        beaconManager.startMonitoring(region)
        beaconManager.startRangingBeacons(region)
        sessionRunning.value = true

        val serviceIntent = Intent(this, ForegroundBeaconScanService::class.java)
        startService(serviceIntent)
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
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

        // Create a OneTimeWorkRequest
        val fileUploadWorkRequest =
            OneTimeWorkRequestBuilder<SessionFilesUploadWorker>().setConstraints(constraints)
                .build()

        // Enqueue the work
        WorkManager.getInstance(this).enqueue(fileUploadWorkRequest)
    }

    fun uploadAll() {
        // Create a OneTimeWorkRequest
        val fileUploadWorkRequest = OneTimeWorkRequestBuilder<SessionFilesUploadWorker>().build()
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
