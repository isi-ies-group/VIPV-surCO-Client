package es.upm.ies.surco.session_logging

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import es.upm.ies.surco.formatAsPathSafeString
import es.upm.ies.surco.notifyObservers
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Status of the session.
 * SESSION_ONGOING: The session is ongoing and data is being collected.
 * SESSION_STOPPING: The session is stopping and data is being saved.
 * SESSION_TRIGGERABLE: The session can be triggered to start or stop.
 */
enum class LoggingSessionStatus {
    SESSION_ONGOING, SESSION_STOPPING, SESSION_TRIGGERABLE,
}

/**
 * Singleton object to hold the logging session and some other metadata.
 *
 * Handles the addition of SensorEntries to the beacons. Creates new instances of Beacon if the
 * identifier is not found in the list.
 *
 * The acquired data is categorized in three different types:
 *   - Time management: start and stop instants of the session and the timezone.
 *   - Android sensors data: GPS and compass data, with their timestamps.
 *   - BLE beacons data: data from the beacons, with their identifiers and the SensorEntries.
 *
 * Time management occupies the least amount of memory, as it is only a few variables.
 * However, the Android sensors data and the BLE beacons data can grow indefinitely,
 * so they may be stored in temporary files to free memory.
 */
object LoggingSession {
    /**
     * Session files prefix and suffix.
     */
    private const val SESSION_FILE_PREFIX = "VIPV_"
    private const val SESSION_FILE_EXTENSION = ".txt"

    /**
     * Temporary files prefix and suffix.
     */
    private const val TEMP_FILE_PREFIX = "dump_"
    private const val TEMP_FILE_EXTENSION = ".temp"

    /**
     * Time span of the session.
     */
    var zone: TimeZone = TimeZone.getDefault()
        private set
    var startZonedDateTime: ZonedDateTime? = null
        private set
    var stopZonedDateTime: ZonedDateTime? = null
        private set

    /**
     * BLE beacons data during the session.
     */
    // Use a Map for O(1) lookups by beacon ID
    private val beaconMap = mutableMapOf<String, BeaconSimplified>()
    val beacons: LiveData<ArrayList<BeaconSimplified>> =
        MutableLiveData<ArrayList<BeaconSimplified>>().apply {
            value = ArrayList(beaconMap.values)
        }
    private var dataCacheFile: File? = null

    /**
     * Number of online beacons tracking in the session.
     */
    private val beaconStatusMap = mutableMapOf<String, BeaconSimplifiedStatus>()
    private val _nBeaconsOnline: MutableLiveData<Int> = MutableLiveData(0)
    val nBeaconsOnline: LiveData<Int> get() = _nBeaconsOnline

    /**
     * Session _status.
     */
    private var _status: MutableLiveData<LoggingSessionStatus> =
        MutableLiveData(LoggingSessionStatus.SESSION_TRIGGERABLE)
    val statusLiveData: LiveData<LoggingSessionStatus> get() = _status
    var status = LoggingSessionStatus.SESSION_TRIGGERABLE
        set(newValue) {
            field = newValue
            _status.postValue(newValue)
        }

    /**
     * Cache directory to store the session files.
     */
    private var sessionsFolder: File? = null

    /**
     * Initialize the singleton with the cache directory.
     */
    fun init(sessionsFolder: File) {
        this.sessionsFolder = sessionsFolder
    }

    /**
     * Adds a SensorEntry to the beacon with the given identifier. If the beacon is not found, it
     * creates a new instance of Beacon and adds it to the list.
     * @param timestamp The timestamp of the data.
     * @param id The identifier of the beacon.
     * @param data The data to be added to the beacon.
     * @param latitude The latitude of the device when the data was acquired.
     * @param longitude The longitude of the device when the data was acquired.
     * @param azimuth The azimuth of the device when the data was acquired.
     */
    fun addBLESensorEntry(
        timestamp: Instant,
        id: String,
        data: Short,
        latitude: Float,
        longitude: Float,
        azimuth: Float
    ) {
        // Look up the beacon in the Map
        val beacon = beaconMap[id]
        if (beacon != null) {
            // Add the sensor entry to the existing beacon
            beacon.addSensorEntry(
                timestamp, data, latitude, longitude, azimuth
            )
        } else {
            // Create a new beacon and add it to the list and the Map
            val newBeacon = BeaconSimplified(id)
            newBeacon.addSensorEntry(
                timestamp, data, latitude, longitude, azimuth
            )
            beaconMap[id] = newBeacon
        }
        (beacons as MutableLiveData).value = ArrayList(beaconMap.values)
        // Notify observers of the updated beaconsData
        beacons.notifyObservers()
        updateBeaconCount()
    }

    /**
     * Returns the list of beacons.
     * @return The list of beacons.
     */
    fun getBeacons(): ArrayList<BeaconSimplified> {
        return ArrayList(beaconMap.values)
    }

    /**
     * Returns the beacon with the given identifier.
     * @param id The identifier of the beacon.
     * @return The beacon with the given identifier.
     */
    fun getBeacon(id: String): BeaconSimplified? {
        return beaconMap[id]
    }

    /**
     * Removes all the data from the session.
     */
    fun clear() {
        beaconMap.clear()
        (beacons as MutableLiveData).notifyObservers()
        beaconStatusMap.clear()
        countOnlineBeacons()
        startZonedDateTime = null
        stopZonedDateTime = null
    }

    /**
     * Removes the beacon with the given identifier from the list.
     * @param id The identifier of the beacon.
     */
    fun removeBeacon(id: String) {
        beaconMap.remove(id)
        (beacons as MutableLiveData).value = ArrayList(beaconMap.values)
        beacons.notifyObservers()
        beaconStatusMap.remove(id)
        countOnlineBeacons()
    }

    /**
     * Update the statuses of all beacons.
     */
    fun refreshBeaconStatuses() {
        beaconMap.forEach { (_, beacon) ->
            beacon.refreshStatus()
        }
    }

    /**
     * Update the count of online beacons in the session.
     */
    private fun updateBeaconCount() {
        _nBeaconsOnline.postValue(beaconMap.values.count { beacon ->
            beacon.statusValue.value != BeaconSimplifiedStatus.OFFLINE
        })
    }

    /**
     * Count the number of online beacons in the session.
     */
    fun countOnlineBeacons() {
        _nBeaconsOnline.postValue(beaconStatusMap.values.count { it != BeaconSimplifiedStatus.OFFLINE })
    }

    /**
     * Save the data to temporary files.
     * Allows updating and merging them later. This is expected to be called when too much data is
     * in memory.
     */
    fun freeDataTemporarily() {
        // create the temporary file if needed
        if (dataCacheFile == null) {
            dataCacheFile =
                File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_EXTENSION, sessionsFolder)
        }

        // append the latest data to the temporary files
        dataCacheFile!!.outputStream().writer(Charsets.US_ASCII).use {
            SessionWriter.appendCsvBodyFromData(it, zone, beacons.value!!)
            it.close()
        }
        // clear the data from the beacons to free memory
        beacons.value?.forEach { it.sensorData.value?.clear() }
    }

    /**
     * Conclude the session and save it to a file.
     * Gets the data from temporary dumps and the current readings.
     * @param maxSessionTimeReached If true, the session has reached the maximum time and
     * should be cleared but not the beacons configuration - this leaves a note in the JSON header.
     * @return The file with the session data.
     */
    fun saveSession(maxSessionTimeReached: Boolean): File? {
        // deep copy all beacons and their data to a temporary variable, and empty them later
        val temporaryBeacons =
            beacons.value!!.filter { beacon -> beacon.isValidInfo() }.map { it.copy() }
        // exit if there is no data to save
        if (temporaryBeacons.isEmpty() || temporaryBeacons.all { beacon -> beacon.sensorData.value?.isEmpty() != false }  // filtered beacons are empty
        ) {
            return null
        }

        val outFile = File(
            sessionsFolder,
            "${SESSION_FILE_PREFIX}${startZonedDateTime!!.formatAsPathSafeString()}-${stopZonedDateTime!!.formatAsPathSafeString()}${SESSION_FILE_EXTENSION}"
        )

        outFile.outputStream().writer(Charsets.UTF_8).use {
            // write the header
            SessionWriter.createJSONHeader(
                it,
                TimeZone.getDefault(),
                temporaryBeacons,
                startZonedDateTime!!,
                stopZonedDateTime!!,
                maxSessionTimeReached = maxSessionTimeReached,
            )
            it.write("\n\n")  // separate the header from the bodies
            // write the beacon CSV header
            SessionWriter.appendCsvHeader(it)
            // write the beacon body from the cached file
            if (dataCacheFile?.exists() == true) {
                SessionWriter.appendCsvBodyFromTempFile(it, dataCacheFile!!)
            }
            // write the beacons body from the beacons list
            SessionWriter.appendCsvBodyFromData(it, zone, temporaryBeacons)
            it.close()
        }

        // clear the temporary file
        dataCacheFile?.delete()
        dataCacheFile = null

        // return the file to the caller
        return outFile
    }

    /**
     * Begins a new session.
     */
    fun beginSession() {
        if (status != LoggingSessionStatus.SESSION_TRIGGERABLE) {
            Log.i(TAG, "Session is not triggerable")
            return
        }
        startZonedDateTime = ZonedDateTime.now()
        status = LoggingSessionStatus.SESSION_ONGOING
    }

    /**
     * Ends the current session by saving it to the cache dir.
     * @return True if the session was successfully concluded and saved, false if there was no data
     * to save
     */
    fun finishSession(maxSessionTimeReached: Boolean = false): Boolean {
        if (status != LoggingSessionStatus.SESSION_ONGOING) {
            return false  // attempted to stop a non-ongoing session
        }
        stopZonedDateTime = ZonedDateTime.now()
        status = LoggingSessionStatus.SESSION_STOPPING
        val outFile = saveSession(maxSessionTimeReached = maxSessionTimeReached)
        if (maxSessionTimeReached) {  // just clear the time series data, not the beacons config
            beacons.value?.map { it.sensorData.value?.clear() }
        } else {  // clear everything
            clear()
        }
        status = LoggingSessionStatus.SESSION_TRIGGERABLE
        return outFile != null
    }

    /**
     * Return a list of Files with the session data in the cache folder, that can be uploaded.
     * Unfinished sessions (i.e., the current one) are not included.
     */
    fun getSessionFiles(): Array<File> {
        // filter out the cached body file
        val files = sessionsFolder?.listFiles { _, name ->
            name.startsWith(SESSION_FILE_PREFIX) && name.endsWith(SESSION_FILE_EXTENSION)
        }
        return files ?: emptyArray()
    }

    private val TAG = this::class.java.simpleName
}
