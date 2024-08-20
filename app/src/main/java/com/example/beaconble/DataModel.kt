package com.example.beaconble

import com.google.gson.annotations.SerializedName
import java.sql.Timestamp

data class SensorData(
    @SerializedName("id_sensor") val id_sensor: String,
    @SerializedName("token")val token: String,
    @SerializedName("timestamp")val timestamp: String,
    @SerializedName("latitud")val latitud: String,
    @SerializedName("longitud")val longitud: String,
    @SerializedName("orientacion")val orientacion: String,
    @SerializedName("inclinacion")val inclinacion: String,
    @SerializedName("tipo_medida")val tipo_medida: String,
    @SerializedName("valor_medida")val valor_medida: String,
    @SerializedName("id")val id: String
)

data class ConfigJSON(
    val id_sensor : Int,
    val token: String
)
