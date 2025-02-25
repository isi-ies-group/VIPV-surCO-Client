package es.upm.ies.surco.session_logging

import java.time.Instant

data class GpsAndCompassInfo(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val compassAngle: Float,
)
