package com.example.beaconble.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.beaconble.BeaconReferenceApplication  // This is a singleton class, access it with BeaconReferenceApplication.instance
import com.example.beaconble.BeaconSimplified
import com.example.beaconble.SensorData
import org.altbeacon.beacon.Identifier
import java.time.Instant

@OptIn(ExperimentalStdlibApi::class)
class FragHomeViewModel() : ViewModel() {
    private val beaconReferenceApplication = BeaconReferenceApplication.instance

    private val _nRangedBeacons = MutableLiveData<Int>()
    val nRangedBeacons: LiveData<Int> get() = _nRangedBeacons
    val rangedBeacons: LiveData<ArrayList<BeaconSimplified>> =
        beaconReferenceApplication.beaconManagementCollection.beacons
    val isSessionActive: LiveData<Boolean> = beaconReferenceApplication.isSessionActive

    init {
        // update the number of beacons detected
        beaconReferenceApplication.nRangedBeacons.observeForever { n ->
            _nRangedBeacons.value = n?.toInt()
        }
    }

    fun sendTestData() {
        Log.d("FragHomeViewModel", "Sending test data")
        beaconReferenceApplication.sendSensorData(
            listOf(
                SensorData(
                    id_sensor = "4001",
                    timestamp = "2021-09-01T12:00:00",
                    latitud = "40.416775",
                    longitud = "-3.703790",
                    orientacion = "0",
                    inclinacion = "0",
                    valor_medida = "25",
                    id = "4"
                )
            )
        )
        beaconReferenceApplication.addSensorDataEntry(
            Identifier.parse("0x1234"),
            25,
            40.416775f,
            -3.703790f,
            Instant.now()
        )
        val nUniqueBeacons =
            beaconReferenceApplication.beaconManagementCollection.beacons.value?.size
        Log.i("FragHomeViewModel", "Unique beacons: $nUniqueBeacons")
    }

    fun toggleSession() {
        beaconReferenceApplication.toggleSession()
    }

    fun emptyAll() {
        beaconReferenceApplication.emptyAll()
    }

    fun exportAll(file: Uri) {
        beaconReferenceApplication.exportAll(file)
    }
}
