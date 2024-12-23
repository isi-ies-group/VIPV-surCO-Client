package com.example.beaconble

import androidx.lifecycle.MutableLiveData
import org.altbeacon.beacon.Identifier
import java.time.Instant

/**
 * In-beacon data entry with the data that changes through a measurement session.
 * Holds the read data (16-bit 2's complement) from the analog channel of the
 * beacon, location and timestamp.
 */
data class SensorEntry(
    val data: Short,
    val latitude: Float,
    val longitude: Float,
    val timestamp: Instant,
)

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
    var description: String = ""
    var direction: Float? = null
    var tilt: Float? = null

    /**
     * Returns the data received from the beacon and clears it.
     */
    fun copyAndClearData(): List<SensorEntry>? {
        val data = sensorData.value?.toList()
        sensorData.value?.clear()
        sensorData.notifyObservers()
        return data
    }
}
