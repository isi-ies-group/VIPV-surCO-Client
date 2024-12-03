package com.example.beaconble

import android.app.*
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.example.beaconble.io_files.SessionWriter
import com.example.beaconble.ui.ActMain
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.ResponseBody
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.MonitorNotifier
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant


class BeaconReferenceApplication : Application() {
    // API service
    private lateinit var ApiService: APIService

    // Bluetooth scanning
    var region = Region(
        "all-beacons",
        null,
        null,
        null
    )  // representa el criterio que se usa para busacar las balizas, como no se quiere buscar una UUID especifica los 3 útlimos campos son null

    // Beacons abstractions
    var beaconManagementCollection = BeaconsCollection()

    // LiveData observers for monitoring and ranging
    lateinit var regionState: MutableLiveData<Int>
    lateinit var rangedBeacons: MutableLiveData<Collection<Beacon>>
    lateinit var nRangedBeacons: MutableLiveData<Int>

    // Data for the beacon session
    var sessionRunning = MutableLiveData<Boolean>(false)
    val isSessionActive: LiveData<Boolean> get() = sessionRunning

    // BeaconManager instance
    private lateinit var beaconManager: BeaconManager

    override fun onCreate() {
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

        // Set API service
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val apiEndpoint = sharedPreferences.getString("api_uri", BuildConfig.SERVER_URL)!!
        setService(apiEndpoint)

        // Save instance for singleton access
        instance = this
    }


    fun setupBeaconScanning() {
        startBeaconScanning()

        // Establecen dos observadores Live Data para cambios en el estado de la región y la lista de beacons detectado
        val regionViewModel = beaconManager.getRegionViewModel(region)
        // Se llamara al observador cuando la region cambie de estado dentro/fuera
        regionViewModel.regionState.observeForever(centralMonitoringObserver)
        // Se llamara al observador cuando se actualice la lista de de beacons (normalmente se actualiza cada 1 seg)
        regionViewModel.rangedBeacons.observeForever(centralRangingObserver)

        // Save observers for public use in ViewModel(s)
        this.regionState = regionViewModel.regionState
        this.rangedBeacons = regionViewModel.rangedBeacons
        this.nRangedBeacons = MutableLiveData(0)
    }

    //registra los cambios de si estas dentro o fuera de la region con la interfaz MonitorNotifier de la biblioteca
    // si estas dentro te envia una notificacion
    val centralMonitoringObserver = Observer<Int> { state ->
        if (state == MonitorNotifier.OUTSIDE) {
            //Log.d(TAG, "Outside beacon region: $region")
        } else {
            //Log.d(TAG, "Inside beacon region: $region")
            // sendNotification()
        }
    }

    //recibe actualizaciones de la lista de beacon detectados y su info
    // hace un calculo del tiempo en millis que ha pasado desde la ultima actualizacion
    val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        val rangeAgeMillis =
            System.currentTimeMillis() - (beacons.firstOrNull()?.lastCycleDetectionTimestamp ?: 0)
        if (rangeAgeMillis < 10000) {
            nRangedBeacons.value = beacons.count()
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
                addSensorDataEntry(
                    id,
                    analogReading,
                    latitude!!.toFloat(),
                    longitude!!.toFloat(),
                    currentInstant
                )
            }
        }
    }

    fun addSensorDataEntry(
        id: Identifier,
        data: Short,
        latitude: Float,
        longitude: Float,
        timestamp: Instant
    ) {
        beaconManagementCollection.addSensorEntry(
            id,
            data,
            latitude,
            longitude,
            timestamp,
        )
    }

    //envia notificacion cuando se detecta un beacon en la region
    private fun sendNotification() {
        val builder = NotificationCompat.Builder(this, "beacon-nearby-notifications-id")
            .setContentTitle("Beacon Reference Application")
            .setContentText("A beacon is nearby.")
            .setSmallIcon(R.mipmap.logo_ies_foreground)
        // TODO: UPDATE i18n strings, double check icon (make one that is a silhouette of a beacon)

        // Explicit intent to open the app when notification is clicked
        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntent(Intent(this, ActMain::class.java))
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(resultPendingIntent)
        val channel = NotificationChannel(
            "beacon-nearby-notifications-id",
            "Beacons Nearby",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Notifies when a beacon is nearby"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        builder.setChannelId(channel.id)
        notificationManager.notify(1, builder.build())
    }

    /**
     * Update the API service endpoint (callback for configuration changes)
     * If the endpoint is not provided or blank, the default value is used (from BuildConfig)
     * @param baseURL New API endpoint
     * @return void
     */
    fun setService(baseURL: String?) {
        var endpoint = baseURL
        if (endpoint.isNullOrBlank()) {
            endpoint = BuildConfig.SERVER_URL
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(endpoint)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        this.ApiService = retrofit.create(APIService::class.java)
    }

    /**
     * Stop monitoring and ranging for beacons
     * @return void
     */
    fun stopBeaconScanning() {
        beaconManager.stopMonitoring(region)
        beaconManager.stopRangingBeacons(region)
        sessionRunning.value = false
    }

    /**
     * Start monitoring and ranging for beacons
     * @return void
     */
    fun startBeaconScanning() {
        beaconManager.startMonitoring(region)
        beaconManager.startRangingBeacons(region)
        sessionRunning.value = true
    }

    /**
     * Iterate over SensorData objects and send them to the server asynchronously
     * @param data List of SensorData objects to send
     * @return Boolean indicating success or failure
     */
    fun sendSensorData(data: List<SensorData>): Boolean {
        for (sensorData in data) {
            ApiService.sendSensorData(
                PreferenceManager.getDefaultSharedPreferences(this).getString("user_token", "")!!,
                sensorData,
            ).enqueue(object : retrofit2.Callback<ResponseBody> {
                override fun onResponse(
                    call: retrofit2.Call<ResponseBody>,
                    response: retrofit2.Response<ResponseBody>
                ) {
                    //Log.d(TAG, "Data sent to server")
                }

                override fun onFailure(call: retrofit2.Call<ResponseBody>, t: Throwable) {
                    Log.e(TAG, "Failed to send data to server")
                }
            })
        }
        return true
    }

    /**
     * Toggle the beacon scanning session
     * @return void
     *
     * Observe sessionRunning LiveData to get the current state of the session
     */
    fun toggleSession() {
        if (sessionRunning.value == true) {
            stopBeaconScanning()
        } else {
            startBeaconScanning()
        }
    }

    fun emptyAll() {
        beaconManagementCollection.emptyAll()
    }

    fun exportAll(outFile: Uri) {
        // Create the output file, with filename VIPV_${TIMESTAMP}.vipv_session
        val outStream = contentResolver.openOutputStream(outFile)
        if (outFile.path.isNullOrBlank()) {
            Log.e(TAG, "Output directory is null or blank")
            return
        }
        SessionWriter.dump2file(outStream!!, beaconManagementCollection.getBeacons())
        outStream.close()
    }

    companion object {
        lateinit var instance: BeaconReferenceApplication
            private set  // This is a singleton, setter is private but access is public
        const val TAG = "BeaconReferenceApplication"
    }  // companion object
}
