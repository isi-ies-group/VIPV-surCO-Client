package com.example.beaconble


import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.Thread.sleep
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {

    //inicializacion de variables
    lateinit var beaconListView: ListView
    lateinit var beaconCountTextView: TextView
    lateinit var monitoringButton: Button
    lateinit var rangingButton: Button
    lateinit var postButton: Button
    lateinit var beaconReferenceApplication: BeaconReferenceApplication
    var alertDialog: AlertDialog? = null
    lateinit var sensorData:SensorData
    var sensorDataList:MutableList<SensorData> = mutableListOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //define la Main Screen con los botones de ranging y monitoring
        beaconReferenceApplication = application as BeaconReferenceApplication

        // Set up a Live Data observer for beacon data
        val regionViewModel = BeaconManager.getInstanceForApplication(this).getRegionViewModel(beaconReferenceApplication.region)
        // Se llama al observador cada vez que la baliza entra/sale de la region
        regionViewModel.regionState.observe(this, monitoringObserver)
        // Se llama al observador cada vez que se actualiza la lista de balizas (normalmetne cada 1 seg en segundo plano)
        regionViewModel.rangedBeacons.observe(this, rangingObserver)
        rangingButton = findViewById<Button>(R.id.rangingButton)
        monitoringButton = findViewById<Button>(R.id.monitoringButton)
        postButton = findViewById<Button>(R.id.postButton)
        beaconListView = findViewById<ListView>(R.id.beaconList)
        beaconCountTextView = findViewById<TextView>(R.id.beaconCount)
        beaconCountTextView.text = "No beacons detected"
        beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
    }


    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
    }

    override fun onResume(){
        super.onResume()
        //al iniciar la actividad se comprueba que los permisos estan aceptados
        if(!BeaconScanPermissionsActivity.allPermissionsGranted(this,true)){
            val intent = Intent (this, BeaconScanPermissionsActivity::class.java)
            intent.putExtra("backgroundAccessRequested", true)
            startActivity(intent)
        }
        else {
            // Todos los permisos se han aceptado
            if (BeaconManager.getInstanceForApplication(this).monitoredRegions.size == 0) {
                beaconReferenceApplication = application as BeaconReferenceApplication
                (application as BeaconReferenceApplication).setupBeaconScanning()
            }
        }

    }

    //monitoring detectar balizas en la region, ranging listar dichas balizas

    //observador de si se encuentra en la region de la baliza
    val monitoringObserver = Observer<Int> { state ->
        var dialogTitle = "Beacons detected"
        var dialogMessage = "didEnterRegionEvent has fired"
        var stateString = "inside"
        if (state == MonitorNotifier.OUTSIDE) {
            dialogTitle = "No beacons detected"
            dialogMessage = "didExitRegionEvent has fired"
            stateString == "outside"
            beaconCountTextView.text = "Outside of the beacon region -- no beacons detected"
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
        }
        else {
            beaconCountTextView.text = "Inside the beacon region."
        }
        Log.d(TAG, "monitoring state changed to : $stateString")
        // configura un alert dialog con un boton de OK
        val builder =
            AlertDialog.Builder(this)
        builder.setTitle(dialogTitle)
        builder.setMessage(dialogMessage)
        builder.setPositiveButton(android.R.string.ok, null)
        alertDialog?.dismiss()
        alertDialog = builder.create()
        alertDialog?.show()
    }

    @SuppressLint("MissingPermission")
    suspend fun addData(context: Context, fielData: String) : SensorData{

        val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        // Solicita la última ubicación conocida
        val location: Location? = fusedLocationClient.lastLocation.await()
        val latitud = location?.latitude?.toString() ?: "0.0"
        val longitud = location?.longitude?.toString() ?: "0.0"

        val sensorData = SensorData(
            id_sensor = "7001",
            token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJmcmVzaCI6ZmFsc2UsImlhdCI6MTcxNjIyOTc0MCwianRpIjoiN2ZhMzRhN2UtNWFlYi00Y2QyLWE4ZjAtNWNmNDViMWU0NGNhIiwidHlwZSI6ImFjY2VzcyIsInN1YiI6NzAwMSwibmJmIjoxNzE2MjI5NzQwLCJleHAiOjE3MTYyMzA2NDB9.VW1Om0dNadi341-T0XZS3exOfG1WRnGGQtZjMd6uPVA",
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME).toString(),
            latitud = latitud,
            longitud = longitud,
            orientacion = "1",
            inclinacion = "1",
            tipo_medida = "irradiancia",
            valor_medida = fielData,
            id = "1"
        )
        return sensorData
    }


    //si esta en el rango hace listado las balizas que hay
    val rangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(TAG, "Ranged: ${beacons.count()} beacons")
        if (BeaconManager.getInstanceForApplication(this).rangedRegions.size > 0) {
            beaconCountTextView.text = "Ranging enabled: ${beacons.count()} beacon(s) detected"

            val beaconInfoList = beacons
                .sortedBy { it.distance }
                .map { beacons ->
                    when {
                        //Eddystone-URL
                        beacons.serviceUuid == 0xfeaa && beacons.beaconTypeCode == 0x10 -> {
                            val url = UrlBeaconUrlCompressor.uncompress(beacons.id1.toByteArray())
                            "URL: ${url}\nrssi: ${beacons.rssi}\nest. distance: ${beacons.distance} m"
                        }
                        //custom
                        beacons.beaconTypeCode== 0x0505 -> {

                            val packet = beacons.lastPacketRawBytes
                            val intData1 = packet[10].toInt()
                            val intData2 = packet[11].toInt()

                            val combinedValue = (intData1 shl 8) or (intData2)
                            val value = combinedValue.toDouble().toString()

                            lifecycleScope.launch{
                                sensorData = addData(this@MainActivity, value)
                                val aux = sensorData
                                sensorDataList.add(aux)
                            }
                            "ID: ${beacons.bluetoothName}\nIRRADIANCIA: ${value}"
                        }
                        //iBeacon
                        else -> {
                            "id1: ${beacons.id1}\nid2: ${beacons.id2} id3:  rssi: ${beacons.rssi}\nest. distance: ${beacons.distance} m"
                        }
                    }
                }
            beaconListView.adapter =
                ArrayAdapter(this, android.R.layout.simple_list_item_1, beaconInfoList)
        }
    }

        //boton para subir los datos
        fun postButtonTapped(view: View){
            if(sensorDataList.isNotEmpty()){
                createPosts(sensorDataList)
            } else{
                Toast.makeText(this, "No data to post.", Toast.LENGTH_SHORT).show()
            }

            sleep(1000)
            sensorDataList.clear()
        }


        //boton para activar/desactivar el ranging
        fun rangingButtonTapped(view: View) {
            val beaconManager = BeaconManager.getInstanceForApplication(this)
            if (beaconManager.rangedRegions.size == 0) {
                beaconManager.startRangingBeacons(beaconReferenceApplication.region)
                rangingButton.text = "Stop Ranging"
                beaconCountTextView.text = "Ranging enabled -- awaiting first callback"
            } else {
                beaconManager.stopRangingBeacons(beaconReferenceApplication.region)
                rangingButton.text = "Start Ranging"
                beaconCountTextView.text = "Ranging disabled -- no beacons detected"
                beaconListView.adapter =
                    ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
            }
        }

        //boton para activar/desactivar el monitoring
        fun monitoringButtonTapped(view: View) {
            var dialogTitle = ""
            var dialogMessage = ""
            val beaconManager = BeaconManager.getInstanceForApplication(this)
            if (beaconManager.monitoredRegions.size == 0) {
                beaconManager.startMonitoring(beaconReferenceApplication.region)
                dialogTitle = "Beacon monitoring started."
                dialogMessage =
                    "You will see a dialog if a beacon is detected, and another if beacons then stop being detected."
                monitoringButton.text = "Stop Monitoring"

            } else {
                beaconManager.stopMonitoring(beaconReferenceApplication.region)
                dialogTitle = "Beacon monitoring stopped."
                dialogMessage =
                    "You will no longer see dialogs when beacons start/stop being detected."
                monitoringButton.text = "Start Monitoring"
            }
            val builder =
                AlertDialog.Builder(this)
            builder.setTitle(dialogTitle)
            builder.setMessage(dialogMessage)
            builder.setPositiveButton(android.R.string.ok, null)
            alertDialog?.dismiss()
            alertDialog = builder.create()
            alertDialog?.show()

        }

    //crear instancia de retrofit
    private fun createRetrofit( baseURL: String): Retrofit {

        return Retrofit.Builder()
            .baseUrl(baseURL)
            .client(getClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    //crear cliente para usar el interceptor
    private fun getClient(): OkHttpClient{
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(MyInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()

        return client
    }

    //bucle de llamada a Retrofit para cada valor de la lista de datos
    private fun createPosts(sensorDataList: List<SensorData>){
        sensorDataList.forEach{ sensorData ->
            createPostApp(sensorData)
        }
    }

    //peticion POST a la URL a traves de Retrofit
    private fun createPostApp(
        sensorData: SensorData
    ) {
        val retrofit = createRetrofit("https://vps247.cesvima.upm.es/")
        val apiService = retrofit.create(APIService::class.java)

        val call = apiService.createPost(sensorData)

        val textView = findViewById<TextView>(R.id.text_central_message)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    println("Created Post: ${response.body()}")
                    runOnUiThread {
                        textView.text = "Post Created ${response.message()}"// Suponiendo que UserInfo tiene un método toString adecuado
                    }
                } else {
                    val error = response.errorBody()
                    println("Error: $error")
                    runOnUiThread {
                        textView.text = "Error: $error"
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println("Failed to create post: ${t.message}")
                runOnUiThread {
                    textView.text = "Falló la conexión: ${t.message}"
                }
            }
        })
    }

    companion object {
        val TAG = "MainActivity"
    }

}