package com.example.beaconble

import java.time.Instant

/**
 * Singleton object to hold the logging session and some other metadata.
 */
object LoggingSession : BeaconsCollection() {
    var startInstant: Instant? = null
    var stopInstant: Instant? = null

    fun clear() {
        beacons.value?.clear()
        startInstant = null
        stopInstant = null
    }
}
