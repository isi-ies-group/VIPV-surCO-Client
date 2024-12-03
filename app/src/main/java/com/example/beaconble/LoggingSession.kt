package com.example.beaconble

import java.time.Instant

/**
 * Singleton object to hold the logging session and some other metadata.
 */
object LoggingSession: BeaconsCollection() {
    private var _startInstant: Instant? = null
    private var _endInstant: Instant? = null

    fun start() {
        _startInstant = Instant.now()
    }

    fun end() {
        _endInstant = Instant.now()
    }

    fun clear() {
        _beacons.value?.clear()
        _startInstant = null
        _endInstant = null
    }
}
