package com.example.beaconble.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.beaconble.AppMain
import com.example.beaconble.BeaconSimplified
import com.example.beaconble.SensorEntry
import org.altbeacon.beacon.Identifier
import java.util.ArrayList

class FragBeaconDetailsViewModel : ViewModel() {
    private var beaconId: Identifier? = null

    private val appMain = AppMain.instance

    private var _beacon = MutableLiveData<BeaconSimplified?>()
    val beacon: LiveData<BeaconSimplified?> get() = _beacon

    var sensorEntries: MutableLiveData<ArrayList<SensorEntry>> =
        MutableLiveData<ArrayList<SensorEntry>>()

    fun getBeaconId(): Identifier? {
        return beaconId
    }

    /**
     * Sets the beacon with the given identifier.
     * @param id The identifier of the beacon.
     */
    fun setBeaconId(id: Identifier) {
        if (id != beaconId) {
            beaconId = id
            _beacon.value = appMain.loggingSession.getBeacon(id)
            sensorEntries = _beacon.value?.sensorData!!
        }
    }

    /**
     * Loads the beacon with the given identifier.
     */
    fun loadBeacon(id: String?) {
        if (id != null) {
            setBeaconId(Identifier.parse(id))
        }
    }

    /**
     * Updates the beacon fields with the given values.
     */
    fun updateBeacon4Fields(description: String, tilt: Float?, direction: Float?) {
        if (_beacon.value != null) {
            _beacon.value?.description = description
            _beacon.value?.tilt = tilt
            _beacon.value?.direction = direction
        }
    }

    /**
     * Deletes the current shown beacon of this ViewModel.
     */
    fun deleteBeacon() {
        if (_beacon.value != null) {
            appMain.loggingSession.removeBeacon(_beacon.value!!.id)
            _beacon.value = null
        }
    }

    companion object {
        private const val TAG = "FragBeaconDetailsViewModel"
    }
}
