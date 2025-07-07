package es.upm.ies.surco.session_logging

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.altbeacon.beacon.Identifier
import java.time.Instant

/**
 * In-beacon data entry with the data that changes through a measurement session.
 * Holds the read data (16-bit 2's complement) from the analog channel of the
 * beacon, location, compass azimuth and timestamp.
 */
data class SensorEntry(
    val timestamp: Instant,
    val data: Short,
    val latitude: Float,
    val longitude: Float,
    val azimuth: Float,
)

/**
 * Status of the beacon simplified.
 */
enum class BeaconSimplifiedStatus {
    OK, OFFLINE, INFO_MISSING,
}

/**
 * Represents a beacon with its identifier and its data. This one is different from the one in the
 * AltBeacon library: this one holds exclusively the data expected to be used in conjunction with
 * the configured beacons and the API service.
 * @param id The identifier of the beacon.
 * @property sensorData The data received from the beacon.
 */
class BeaconSimplified(val id: Identifier) {
    /**
     * The data received from the analog channel of the beacon. From the NanoBeacon Config Tool User Guide EN.pdf:
     * "The ADC data is of 16-bit in 2’s complement format."
     * Set initial capacity of 360.
     */
    var sensorData: MutableLiveData<ArrayList<SensorEntry>> =
        MutableLiveData<ArrayList<SensorEntry>>(ArrayList(360))
    private var description: String = ""
    val descriptionValue get() = description
    private var position: String = ""
    val positionValue get() = position
    private var tilt: Float? = 0.0f  // Default value is 0.0f, can be null if not set
    val tiltValue get() = tilt
    private val status: MutableLiveData<BeaconSimplifiedStatus> =
        MutableLiveData(BeaconSimplifiedStatus.INFO_MISSING)
    val statusValue: LiveData<BeaconSimplifiedStatus> get() = status

    /**
     * Set the description, tilt, and position of the beacon, and refresh the status.
     */
    fun setDescription(description: String) {
        this.description = description
        refreshStatus()
    }

    fun setTilt(tilt: Float?) {
        this.tilt = tilt
        refreshStatus()
    }

    fun setPosition(position: String) {
        this.position = position
        if (this.position == "select_position_default") {
            this.position = ""
        }
        refreshStatus()
    }

    /**
     * Refreshes the status of the beacon.
     *
     * If the last data entry is older than the maximum offset, the status is set to OFFLINE.
     * If the info fields (position, or tilt) are empty, the status is set to INFO_MISSING.
     * Else, the status is set to OK.
     * @return The new status of the beacon.
     */
    fun refreshStatus(): BeaconSimplifiedStatus {
        val lastEntry = sensorData.value?.lastOrNull()
        val now = Instant.now()

        val newStatus = when {
            // Ordered by what is most important to report
            lastEntry == null || (now.epochSecond - lastEntry.timestamp.epochSecond > MAX_SECONDS_OFFSET_UNTIL_OUT_OF_RANGE) -> BeaconSimplifiedStatus.OFFLINE
            !isValidInfo() -> BeaconSimplifiedStatus.INFO_MISSING
            else -> BeaconSimplifiedStatus.OK
        }

        status.postValue(newStatus)
        return newStatus
    }

    /**
     * Checks if the beacon has valid information.
     */
    fun isValidInfo(): Boolean {
        return !(position.isEmpty() || tilt == null)
    }

    /**
     * Deep copy of the beacon data.
     */
    fun copy(): BeaconSimplified {
        val copy = BeaconSimplified(id)
        val originalSensorData = sensorData.value ?: emptyList()
        val copiedSensorData = ArrayList<SensorEntry>(originalSensorData.size)
        copiedSensorData.addAll(originalSensorData)
        copy.sensorData = MutableLiveData(copiedSensorData)
        copy.description = description
        return copy
    }

    /**
     * Clears the data of the beacon.
     */
    fun clear() {
        sensorData.value?.clear()
        description = ""
        tilt = 0.0f
    }

    /**
     * toString representation of the beacon.
     */
    override fun toString(): String {
        return "BeaconSimplified(id=$id, position='$position', tilt=$tilt, status=${status.value}, description='$description')"
    }

    /**
     * Adds a SensorEntry to the beacon, updates the status and notifies observers.
     */
    fun addSensorEntry(
        timestamp: Instant, data: Short, latitude: Float, longitude: Float, azimuth: Float
    ) {
        // Add the sensor entry to the existing beacon
        sensorData.value?.add(SensorEntry(timestamp, data, latitude, longitude, azimuth))
        sensorData.postValue(sensorData.value)
        // Update the status
        refreshStatus()
    }

    companion object {
        const val MAX_SECONDS_OFFSET_UNTIL_OUT_OF_RANGE = 2.5
    }
}
