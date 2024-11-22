package com.example.beaconble

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.beaconble.BeaconReferenceApplication  // This is a singleton class, access it with BeaconReferenceApplication.instance
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.MonitorNotifier

@OptIn(ExperimentalStdlibApi::class)
class FragHomeViewModel() : ViewModel() {
    private val _exampleData = MutableLiveData<Array<String>>()
    val exampleData: LiveData<Array<String>> get() = _exampleData
    private val _topMessage = MutableLiveData<String>()
    val topMessage: LiveData<String> get() = _topMessage

    private val beaconReferenceApplication = BeaconReferenceApplication.instance

    init {
        beaconReferenceApplication.regionState.observeForever { state ->
            _topMessage.value = when (state) {
                MonitorNotifier.INSIDE -> "Inside region"
                MonitorNotifier.OUTSIDE -> "Outside region"
                else -> "Unknown state"
            }
        }
        beaconReferenceApplication.rangedBeacons.observeForever { beacons ->
            val beaconNames = beacons.map { it.beaconTypeCode.toHexString() }.toTypedArray()
            _exampleData.value = beaconNames
        }
    }

    fun sendTestData() {
        beaconReferenceApplication.sendSensorData(
            listOf(
                SensorData(
                    id_sensor = "4001",
                    timestamp = "2021-09-01T12:00:00",
                    latitud = "40.416775",
                    longitud = "-3.703790",
                    orientacion = "0",
                    inclinacion = "0",
                    tipo_medida = "irradiancia",
                    valor_medida = "25",
                    id = "4"
                )
            )
        )
    }
}
