package com.example.beaconble.ui

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.beaconble.AppMain  // This is a singleton class, access it with BeaconReferenceApplication.instance
import com.example.beaconble.BeaconSimplified

@OptIn(ExperimentalStdlibApi::class)
class FragHomeViewModel() : ViewModel() {
    private val appMain = AppMain.instance

    private val _nRangedBeacons = MutableLiveData<Int>()
    val nRangedBeacons: LiveData<Int> get() = _nRangedBeacons
    val rangedBeacons: LiveData<ArrayList<BeaconSimplified>> =
        appMain.loggingSession.beacons
    val isSessionActive: LiveData<Boolean> = appMain.isSessionActive

    init {
        // update the number of beacons detected
        appMain.nRangedBeacons.observeForever { n ->
            _nRangedBeacons.value = n?.toInt()
        }
    }

    /*fun sendTestData() {
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
    }*/

    fun toggleSession() {
        appMain.toggleSession()
    }

    fun emptyAll() {
        appMain.emptyAll()
    }

    fun exportAll(file: Uri) {
        appMain.exportAll(file)
    }
}
