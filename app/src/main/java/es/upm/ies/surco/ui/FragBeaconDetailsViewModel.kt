package es.upm.ies.surco.ui

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import es.upm.ies.surco.AppMain
import es.upm.ies.surco.session_logging.BeaconSimplified
import es.upm.ies.surco.session_logging.BeaconSimplifiedStatus
import es.upm.ies.surco.session_logging.SensorEntry
import kotlin.math.atan
import kotlin.math.sqrt

class FragBeaconDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val appMain by lazy { getApplication<AppMain>() }
    private var beaconId: String? = null

    private var _beacon = MutableLiveData<BeaconSimplified?>()
    val beacon: LiveData<BeaconSimplified?> get() = _beacon
    val status: LiveData<BeaconSimplifiedStatus>? get() = _beacon.value?.statusValue

    var sensorEntries: MutableLiveData<ArrayList<SensorEntry>> =
        MutableLiveData<ArrayList<SensorEntry>>()

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate tilt angle (-90° to +90° range)
            // 0° when device is flat, 90° when vertical
            var tilt = Math.toDegrees(atan(sqrt((x * x + y * y)) / z).toDouble()).toFloat()
            if (tilt < 0.0f) {
                tilt += 180.0f // Adjust to ensure tilt is in the range of 0° to 180°
            }
            _tiltAngle.postValue(tilt)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Unused, required by the interface
        }
    }

    private val _tiltAngle = MutableLiveData<Float?>()
    val tiltAngle: LiveData<Float?> get() = _tiltAngle

    init {
        initializeSensors()
    }

    /**
     * Sets the beacon with the given identifier.
     * @param id The identifier of the beacon.
     */
    fun setBeaconId(id: String) {
        if (id != beaconId) {
            beaconId = id
            _beacon.value = appMain.loggingSession.getBeacon(id)
            sensorEntries = _beacon.value?.sensorData ?: MutableLiveData<ArrayList<SensorEntry>>()
        }
    }

    /**
     * Loads the beacon with the given identifier.
     */
    fun loadBeacon(id: String) {
        setBeaconId(id)
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

    private fun initializeSensors() {
        sensorManager = getApplication<Application>().getSystemService(Application.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }
}
