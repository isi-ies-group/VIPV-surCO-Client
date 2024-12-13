package com.example.beaconble.service

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Observer
import com.example.beaconble.AppMain.Companion.TAG
import com.example.beaconble.BuildConfig
import com.example.beaconble.LoggingSession
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region

class ForegroundBeaconScanService: Service() {
    val loggingSession: LoggingSession = LoggingSession

    // For AltBeacon to work
    var region = Region("all-beacons", null, null, null)
    // BeaconManager instance
    private lateinit var beaconManager: BeaconManager

    val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        val rangeAgeMillis =
            System.currentTimeMillis() - (beacons.firstOrNull()?.lastCycleDetectionTimestamp ?: 0)
        if (rangeAgeMillis < 10000) {
            // get location latitude and longitude, common for all beacons detected here
            val fusedLocationClient: FusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(applicationContext)
            var location: Location? = null
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { loc: Location? ->
                        // Got last known location. In some rare situations this can be null.
                        location = loc
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission denied")
                // TODO handle location permission denied
            }
            // if location is null, set latitude and longitude to NaN
            var latitude = location?.latitude
            var longitude = location?.longitude
            if (location == null) {
                Log.e(TAG, "Location is null")
                latitude = Double.NaN
                longitude = Double.NaN
            }

            // iterate over beacons and log their data
            val currentInstant = java.time.Instant.now()
            for (beacon: Beacon in beacons) {
                val id = beacon.id1
                Log.i(TAG, "ID: $id")
                val data = beacon.dataFields
                // analogReading is the CH1 analog value, as two bytes in little endian
                val analogReading = data[0].toShort()
                val hexString = analogReading.toString(16)
                Log.i(TAG, "Data: $analogReading (0x$hexString)")
                loggingSession.addSensorEntry(
                    id,
                    analogReading,
                    latitude!!.toFloat(),
                    longitude!!.toFloat(),
                    currentInstant
                )
            }
        }
    }

    fun onStartCommand() {
        // Start the service
    }
    override fun onBind(p0: Intent?): IBinder? {
        // Bind the service
        return null
    }
    override fun onCreate() {
        // Create the service
        super.onCreate()

        beaconManager = BeaconManager.getInstanceForApplication(this)
        // Beacon Manager configura la interaccion con las beacons y el start/stop de ranging/monitoring

        // Por defecto la biblioteca solo detecta AltBeacon si se quiere otro tipo de protocolo hay que añadir el layout
        // m:0-1=0505 stands for InPlay's Company Identifier Code (0x0505), see https://www.bluetooth.com/specifications/assigned-numbers/
        // i:2-7 stands for the identifier, UUID (MAC) [little endian]
        // d:8-9 stands for the data, CH1 analog value [little endian]
        val customParser = BeaconParser().setBeaconLayout("m:0-1=0505,i:2-7,d:8-9")
        beaconManager.beaconParsers.add(customParser)

        // Activate debug mode only if build variant is debug
        BeaconManager.setDebug(BuildConfig.DEBUG)

        //configurar escaneo
        setupBeaconScanning()
    }
    override fun onDestroy() {
        // Destroy the service
        super.onDestroy()
    }

    fun setupBeaconScanning() {
        startBeaconScanning()

        // Establecen dos observadores Live Data para cambios en el estado de la región y la lista de beacons detectado
        val regionViewModel = beaconManager.getRegionViewModel(region)
        // Se llamara al observador cuando la region cambie de estado dentro/fuera
        //regionViewModel.regionState.observeForever(centralMonitoringObserver)
        // Se llamara al observador cuando se actualice la lista de de beacons (normalmente se actualiza cada 1 seg)
        regionViewModel.rangedBeacons.observeForever(centralRangingObserver)
    }

    fun startBeaconScanning() {
        // Start ranging for all beacons
        beaconManager.startMonitoring(region)
        beaconManager.startRangingBeacons(region)
    }
}
