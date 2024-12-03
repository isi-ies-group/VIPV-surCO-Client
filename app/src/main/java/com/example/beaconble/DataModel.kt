package com.example.beaconble

import com.google.gson.annotations.SerializedName

@Suppress("PropertyName")
data class SensorData(
    @SerializedName("id_sensor") val id_sensor: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("latitud") val latitud: String,
    @SerializedName("longitud") val longitud: String,
    @SerializedName("orientacion") val orientacion: String,
    @SerializedName("inclinacion") val inclinacion: String,
    @SerializedName("valor_medida") val valor_medida: String,
    @SerializedName("id") val id: String
)
