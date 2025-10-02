package es.upm.ies.surco

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Intent
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import es.upm.ies.surco.api.APIService
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.service.ForegroundBeaconScanService
import es.upm.ies.surco.session_logging.LoggingSession
import es.upm.ies.surco.session_logging.LoggingSessionStatus
import es.upm.ies.surco.ui.ActMain
import es.upm.ies.surco.workers.SessionFilesUploadWorker
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import kotlin.concurrent.thread
import androidx.core.content.edit


class AppMain : Application(), ComponentCallbacks2 {
    // API & user services
    private lateinit var apiService: APIService
    var apiServerUri: String? = null
        private set  // modification to endpoint URI must be done through setupApiService()

    // Beacons abstractions
    var loggingSession = LoggingSession
    lateinit var beaconManager: BeaconManager

    // -- for public use by UI --
    val wasUploadedSuccessfully = MutableLiveData(false)  // set by the worker

    val minSensorAccuracy = MutableLiveData(SensorManager.SENSOR_STATUS_UNRELIABLE)
    val sensorAccuracyValue: LiveData<Int> get() = minSensorAccuracy

    // Data for the beacon session
    var scanInterval: Long = DEFAULT_SCAN_WINDOW_INTERVAL

    // Status update handler
    // This handler is used to update the statuses of the beacons every STATUS_UPDATE_INTERVAL milliseconds
    private val handler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            loggingSession.refreshBeaconStatuses()
            handler.postDelayed(this, STATUS_UPDATE_INTERVAL)
        }
    }

    val midnightRunnable = Runnable {
        Log.i(TAG, "Executing midnight session stop")
        restartCurrentSession()
    }

    val cachedSessionsDir by lazy {
        cacheDir.resolve(SESSIONS_SUBDIR_IN_CACHE)
    }

    override fun onCreate() {
        super.onCreate()

        // Assign the singleton instance
        instance = this

        // Initialize the shared preferences
        sharedPreferencesPortBetweenVersions()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Create the cached sessions directory if it does not exist
        if (!cachedSessionsDir.exists()) {
            cachedSessionsDir.mkdirs()
        }
        // Session initialization
        loggingSession.init(cachedSessionsDir)

        // Initialize the API user session before initializing the API user wrapper
        setupApiService()

        // Set the theme
        val theme = sharedPreferences.getString("color_theme", "system-default") ?: "system-default"
        setupTheme(theme)
        // Load the scan interval
        try {
            scanInterval = sharedPreferences.getString("scan_interval", null)?.toLong()
                ?: DEFAULT_SCAN_WINDOW_INTERVAL
        } catch (_: NumberFormatException) {
            Log.e(TAG, "Invalid scan interval")
            scanInterval = DEFAULT_SCAN_WINDOW_INTERVAL
        }

        beaconManager = BeaconManager.getInstanceForApplication(this)
        // By default, the library will detect AltBeacon protocol beacons
        beaconManager.beaconParsers.clear()
        // m:0-1=0505 stands for InPlay's Company Identifier Code (0x0505),
        // see https://www.bluetooth.com/specifications/assigned-numbers/
        // i:2-7 stands for the identifier, UUID (MAC) [little endian]
        // d:8-9 stands for the data, CH1 analog value [little endian]
        val customParser = BeaconParser().setBeaconLayout("m:0-1=0505,i:2-7,d:8-9")
        beaconManager.beaconParsers.add(customParser)

        // Activate debug mode only if build variant is debug
        BeaconManager.setDebug(BuildConfig.DEBUG)
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

    override fun onTerminate() {
        stopMeasurementsSession()
        super.onTerminate()
    }

    /**
     * Add sensor data entries to the loggingSession from the beacon data returned by the ranging
     * @param beacons: Collection<Beacon>, list of beacons detected by the ranging
     * @param timestamp: Instant, timestamp of the data
     */
    fun addBeaconCollectionData(
        beacons: Collection<Beacon>,
        timestamp: Instant,
        latitude: Float,
        longitude: Float,
        azimuth: Float
    ) {
        for (beacon in beacons) {
            val id = beacon.id1
            val data = beacon.dataFields
            // analogReading is the CH1 analog value, as two bytes in little endian
            val analogReading = data[0].toShort()
            addSensorDataEntry(
                timestamp, id.toString(), analogReading, latitude, longitude, azimuth
            )
        }
    }

    /**
     * Add sensor data entry to the loggingSession
     * @param timestamp: Instant, timestamp of the data
     * @param id: String, identifier of the beacon
     * @param data: Short, data to be added to the beacon
     *
     */
    fun addSensorDataEntry(
        timestamp: Instant,
        id: String,
        data: Short,
        latitude: Float,
        longitude: Float,
        azimuth: Float
    ) {
        loggingSession.addBLESensorEntry(timestamp, id, data, latitude, longitude, azimuth)
    }

    /**
     * Setup the API service endpoint (callback for configuration changes)
     * If the endpoint is not set in PreferenceManager.getDefaultSharedPreferences,
     * the default value is used (from BuildConfig)
     *
     * Note you must call the logout function before changing the API service endpoint
     * to ensure the user session is reset.
     *
     * @param newUri: String, new URI to set for the API service if known, else it is read from shared preferences
     * @return void
     */
    fun setupApiService(newUri: String? = null) {
        if (newUri.isNullOrBlank()) {  // If no new URI is provided, use the default from shared preferences
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
        if (newUri == apiServerUri) {  // If the new URI is the same as the current one, do nothing
            Log.i(TAG, "API service already set to $newUri")
            return
        } else {  // If the new URI is different, set it up
            this.apiServerUri = newUri
            if (!apiServerUri!!.endsWith("/")) {
                apiServerUri += "/"
            }
            if (!apiServerUri!!.startsWith("http://") && !apiServerUri!!.startsWith("https://")) {
                apiServerUri = "http://${apiServerUri!!}"
            }
            val retrofit = Retrofit.Builder().baseUrl(apiServerUri!!)
                .addConverterFactory(GsonConverterFactory.create()).build()
            this.apiService = retrofit.create(APIService::class.java)
            // Inject new API service into ApiActions
            ApiActions.initialize(PreferenceManager.getDefaultSharedPreferences(this), apiService)
        }
    }

    /**
     * Test the API endpoint by sending a GET request to the server
     */
    suspend fun testApiEndpoint(): Boolean {
        try {
            val response = apiService.up()
            Log.i(TAG, "API is up: $response")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "API is down: $e")
            return false
        }
    }

    fun isForegroundBeaconScanServiceRunning() =
        isServiceRunning(ForegroundBeaconScanService::class.java)

    /**
     * Master function to start a beacon scanning session.
     * It will make sure the session is in the correct state before starting the scanning service.
     * @return LoggingSessionStatus, the status of the session after trying to start it
     */
    private fun startMeasurementsSession() {
        if (loggingSession.status == LoggingSessionStatus.SESSION_ONGOING) {
            Log.i(TAG, "Session is already ongoing")
            return
        }
        if (loggingSession.status == LoggingSessionStatus.SESSION_STOPPING) {
            Log.i(TAG, "Session is finalizing, cannot start a new one")
            return
        }
        Log.i(TAG, "Starting new session")
        // Start a new session
        loggingSession.beginSession()
        // Start the foreground beacon scanning service
        startForegroundBeaconScanningProcess()
        // Start periodic updates of the beacon statuses
        startStatusPollingOfBeacons()
        // Start the session length timer
        scheduleMidnightSessionRestartIfUnsupervised()
        Log.i(TAG, "Session started")
    }

    /**
     * Master function to stop a beacon scanning session.
     * It will make sure the session is in the correct state before stopping the scanning service.
     * @return LoggingSessionStatus, the status of the session after trying to stop it
     */
    private fun stopMeasurementsSession() {
        Log.i(TAG, "Stopping current session if any")
        cancelMidnightSessionRestartIfUnsupervised()
        stopStatusPollingOfBeacons()
        stopForegroundBeaconScanningProcess()
        finishCurrentSessionAndScheduleUpload()
    }

    /**
     * Public function to stop the current session if any
     * @return void
     */
    fun requestStopCurrentSession() {
        if (loggingSession.status == LoggingSessionStatus.SESSION_ONGOING) {
            stopMeasurementsSession()
        }
    }

    /**
     * Restart the current session if there is one ongoing
     * Currently only runs if unsupervised mode is enabled, at midnight restarts
     * @return void
     */
    private fun restartCurrentSession() {
        Log.i(TAG, "Restarting current session")
        // Check if session is still ongoing before relaunching
        if (LoggingSession.status == LoggingSessionStatus.SESSION_ONGOING) {
            // Keep the beacons configuration
            scheduleMidnightSessionRestartIfUnsupervised()
            finishCurrentSessionAndScheduleUpload(endedByUnsupervisedMode = true)
            // Start a new session
            loggingSession.beginSession()
            Log.i(TAG, "Session restarted")
        }
    }

    /**
     * Finish the current session if any
     */
    private fun finishCurrentSessionAndScheduleUpload(endedByUnsupervisedMode: Boolean = false) {
        val successfulSession =
            loggingSession.finishSession(endedByUnsupervisedMode = endedByUnsupervisedMode)
        if (successfulSession) {
            Log.i(TAG, "Session stopped successfully")
            // Schedule the file upload if the session was valid, in a concurrent thread
            thread {
                scheduleFileUploadWorkerWithPreferences()
            }
        } else {
            Log.i(TAG, "Session stopped but it was not valid")
        }
    }

    /**
     * Start the foreground beacon scanning service
     * @return void
     */
    private fun startForegroundBeaconScanningProcess() {
        Log.i(TAG, "Starting foreground beacon scanning service")
        if (isForegroundBeaconScanServiceRunning()) return
        val serviceIntent = Intent(this, ForegroundBeaconScanService::class.java)
        startService(serviceIntent)
    }

    /**
     * Stop the foreground beacon scanning service
     * @return void
     */
    private fun stopForegroundBeaconScanningProcess() {
        Log.i(TAG, "Stopping foreground beacon scanning service")
        val serviceIntent = Intent(this, ForegroundBeaconScanService::class.java)
        stopService(serviceIntent)
    }

    /**
     * Starts a periodic refresh of the beacon statuses
     * @return void
     */
    private fun startStatusPollingOfBeacons() {
        Log.i(TAG, "Starting status polling of beacons")
        handler.post(statusUpdateRunnable) // Start periodic statusLiveData updates of the beacon statuses
    }

    /**
     * Stops a periodic refresh of the beacon statuses
     * @return void
     */
    private fun stopStatusPollingOfBeacons() {
        Log.i(TAG, "Stopping status polling of beacons")
        handler.removeCallbacks(statusUpdateRunnable)  // Stop periodic statusLiveData updates
    }

    /**
     * Start whether to automatically stop the session automatically (in unsupervised mode).
     * @return void
     */
    private fun scheduleMidnightSessionRestartIfUnsupervised() {
        Log.i(TAG, "Scheduling session restart if unsupervised")
        // Cancel any existing timer
        cancelMidnightSessionRestartIfUnsupervised()

        // Check if unsupervised mode is enabled
        val unsupervisedMode = this.let {
            val prefs = PreferenceManager.getDefaultSharedPreferences(it)
            prefs.getBoolean("unsupervised_mode", false)
        }

        // Only start timer unsupervised mode is enabled
        if (unsupervisedMode) {
            val now = System.currentTimeMillis()
            val timeToMidnight =
                calculateMillisFromTo(now, 0, 0, 0) - 100L // 100 ms before midnight
            Log.i(TAG, "Scheduling unsupervised session stop at midnight in $timeToMidnight ms")

            handler.removeCallbacks(midnightRunnable)
            handler.postDelayed(midnightRunnable, timeToMidnight)
        }
    }

    /**
     * Stop whether to automatically stop the session automatically (in unsupervised mode).
     * @return void
     */
    private fun cancelMidnightSessionRestartIfUnsupervised() {
        Log.i(TAG, "Canceling session restart if unsupervised")
        handler.removeCallbacks(midnightRunnable)
    }

    /**
     * Public callback function to launch the future relaunch if unsupervised.
     * This is used when the user changes the unsupervised mode setting.
     * @return void
     */
    fun onChangedSessionFutureRelaunchIfUnsupervised() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val unsupervisedMode = sharedPreferences.getBoolean("unsupervised_mode", false)
        Log.i(TAG, "Unsupervised mode changed to $unsupervisedMode")
        if (unsupervisedMode) {
            scheduleMidnightSessionRestartIfUnsupervised()
        } else {
            cancelMidnightSessionRestartIfUnsupervised()
        }
    }

    /**
     * Schedule file upload worker
     * @return void
     */
    private fun scheduleFileUploadWorkerWithPreferences() {
        Log.i(TAG, "Scheduling file upload worker based on preferences")
        // if the sharedPreference is set to upload files on metered network, schedule the upload
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val autoUploadBehaviour =
            sharedPreferences.getString("auto_upload_behaviour", "auto_un_metered")
        when (autoUploadBehaviour) {
            "auto_un_metered" -> scheduleFileUpload(now = false)
            "auto_always" -> scheduleFileUpload(now = true)
            // else, manual upload -> do nothing
        }
    }


    /**
     * Toggle the beacon scanning session from opened UI button. Shows a toast message.
     * @return void
     *
     * Observe sessionRunning LiveData to get the current state of the session
     */
    fun toggleSession() {
        Log.i(TAG, "Toggling session")
        if (loggingSession.status == LoggingSessionStatus.SESSION_ONGOING) {
            stopMeasurementsSession()
        } else if (loggingSession.status == LoggingSessionStatus.SESSION_TRIGGERABLE) {
            startMeasurementsSession()
        }
    }

    /**
     * Schedule a file upload to the server
     * @param now: Boolean, true if the upload should be done immediately, false otherwise
     * @return void
     */
    fun scheduleFileUpload(now: Boolean) {
        Log.i(TAG, "Scheduling file upload ${if (now) "now" else "later"}")
        // Create a OneTimeWorkRequest
        val fileUploadWorkRequestBuilder = OneTimeWorkRequestBuilder<SessionFilesUploadWorker>()

        if (now) {
            // Define constraints for non-metered networks
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
            fileUploadWorkRequestBuilder.setConstraints(constraints)
        }

        val fileUploadWorkRequest = fileUploadWorkRequestBuilder.build()

        // Enqueue the work
        WorkManager.getInstance(this).enqueue(fileUploadWorkRequest)
    }

    fun uploadAllSessions() {
        // Create a OneTimeWorkRequest
        val fileUploadWorkRequest = OneTimeWorkRequestBuilder<SessionFilesUploadWorker>().build()
        // Enqueue the work
        WorkManager.getInstance(this).enqueue(fileUploadWorkRequest)
    }

    fun deleteAllSessions(callback: (Boolean) -> Unit) {
        thread {
            val files = loggingSession.getSessionFiles()
            var success = true
            files.forEach { file ->
                if (file.exists() && !file.delete()) {
                    success = false
                }
            }
            Handler(Looper.getMainLooper()).post {
                callback(success)
            }
        }
    }

    /**
     * Check if a service is running.
     * @param serviceClass: Class<*>, the class of the service to check
     * @return Boolean, true if the service is running, false otherwise
     *
     * This function is used to check if the ForegroundBeaconScanService is running.
     *
     * Notes:
     * 1. getRunningServices() is deprecated in newer Android versions (API level 21 and above),
     *    and it might not provide full reliability for services running in the background.
     * 2. Starting from Android 5.0 (API level 21), apps can no longer directly access information
     *    about other apps' services due to increased restrictions on background processes.
     */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager

        @Suppress("DEPRECATION") val runningServices = manager.getRunningServices(Int.MAX_VALUE)

        for (service in runningServices) {
            if (service.service.className == serviceClass.name) {
                return true
            }
        }
        return false
    }

    /**
     * Setup the application theme (callback for configuration changes)
     */
    fun setupTheme(theme: String) {
        Log.i(TAG, "Setting theme to $theme")
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> {  // default to system theme, may be default key "system-default"
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        // Restart the activity to apply the new theme
        val intent = Intent(this, ActMain::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    /**
     * Port configuration values from old versions to new ones if needed
     * @return void
     */
    private fun sharedPreferencesPortBetweenVersions() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // remove max_session_duration if it exists
        if (sharedPreferences.contains("max_session_duration")) {
            sharedPreferences.edit { remove("max_session_duration") }
        }
    }

    companion object {
        private var instance: AppMain? = null

        fun getInstance(): AppMain {
            return instance ?: throw IllegalStateException("AppMain not initialized")
        }

        const val TAG = "AppMain"
        const val SESSIONS_SUBDIR_IN_CACHE = "sessions"
        const val NOTIFICATION_ONGOING_SESSION_ID = 1
        const val NOTIFICATION_NO_LOCATION_OR_BLUETOOTH_ID = 2
        const val ACTION_STOP_SESSION = "es.upm.ies.surco.STOP_SESSION"
        const val GPS_LOCATION_PERIOD_MILLIS = 1000L  // 1 second
        const val STATUS_UPDATE_INTERVAL = 2000L  // 2 seconds
        const val DEFAULT_SCAN_WINDOW_INTERVAL = 50L  // 50 milliseconds
    }  // companion object
}
