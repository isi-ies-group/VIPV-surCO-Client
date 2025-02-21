package es.upm.ies.vipvble.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import es.upm.ies.vipvble.AppMain
import es.upm.ies.vipvble.BeaconSimplified
import es.upm.ies.vipvble.BeaconSimplifiedStatus
import es.upm.ies.vipvble.SensorEntry
import org.altbeacon.beacon.Identifier
import java.util.ArrayList

class FragBeaconDetailsViewModel : ViewModel() {
    private var beaconId: Identifier? = null

    private val appMain = AppMain.Companion.instance

    private var _beacon = MutableLiveData<BeaconSimplified?>()
    val beacon: LiveData<BeaconSimplified?> get() = _beacon
    val status: LiveData<BeaconSimplifiedStatus>? get() = _beacon.value?.statusValue

    var sensorEntries: MutableLiveData<ArrayList<SensorEntry>> =
        MutableLiveData<ArrayList<SensorEntry>>()

    /**
     * Sets the beacon with the given identifier.
     * @param id The identifier of the beacon.
     */
    fun setBeaconId(id: Identifier) {
        if (id != beaconId) {
            beaconId = id
            _beacon.value = appMain.loggingSession.getBeacon(id)
            sensorEntries = _beacon.value?.sensorData ?: MutableLiveData<ArrayList<SensorEntry>>()
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
     * Deletes the current shown beacon of this ViewModel.
     */
    fun deleteBeacon() {
        if (_beacon.value != null) {
            appMain.loggingSession.removeBeacon(_beacon.value!!.id)
            _beacon.value = null
        }
    }
}
