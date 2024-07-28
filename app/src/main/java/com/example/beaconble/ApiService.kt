package com.example.beaconble

import android.hardware.Sensor
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST


interface APIService{
    @Headers(
        "Content-type: application/x-www-form-urlencoded",
        "token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJmcmVzaCI6ZmFsc2UsImlhdCI6MTcxNjIyOTc0MCwianRpIjoiN2ZhMzRhN2UtNWFlYi00Y2QyLWE4ZjAtNWNmNDViMWU0NGNhIiwidHlwZSI6ImFjY2VzcyIsInN1YiI6NzAwMSwibmJmIjoxNzE2MjI5NzQwLCJleHAiOjE3MTYyMzA2NDB9.VW1Om0dNadi341-T0XZS3exOfG1WRnGGQtZjMd6uPVA"
    )

    @POST("addData")
    fun createPost(
        @Body body: SensorData): Call<ResponseBody>
}


