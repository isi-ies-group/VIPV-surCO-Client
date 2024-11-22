package com.example.beaconble

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST


interface APIService {
    @Headers("Content-type: application/x-www-form-urlencoded")
    @POST("addData")
    fun sendSensorData(
        @Header ("token") token: String,
        @Body body: SensorData,
    ): Call<ResponseBody>
}


