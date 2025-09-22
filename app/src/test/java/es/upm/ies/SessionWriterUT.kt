package es.upm.ies

import es.upm.ies.surco.BuildConfig
import es.upm.ies.surco.session_logging.SessionWriter
import es.upm.ies.surco.session_logging.BeaconSimplified
import es.upm.ies.surco.session_logging.LoggingSession
import es.upm.ies.surco.session_logging.SensorEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionWriterUT {
    @Test
    fun createJSONHeader() {
        val beacons = arrayListOf<BeaconSimplified>()

        // add two example beacons
        val beacon0 = BeaconSimplified("01:02:03:04:05:06")
        beacon0.apply {
            setTilt(0.0f)
            setDescription("Soy la cosita más linda y mona de este mundo.")
            setPosition("trunk")
        }
        beacons.add(beacon0)

        val beacon1 = BeaconSimplified("01:02:03:04:05:07")
        beacon1.apply {
            setTilt(10.0f)
            setDescription("Soy la cosita más linda y mona de este mundo.")
            setPosition("roof")
        }
        beacons.add(beacon1)

        // set start and finish instants
        val startZonedDateTime = ZonedDateTime.parse("2021-10-01T12:00:00Z")
        val stopZonedDateTime = ZonedDateTime.parse("2021-10-01T12:30:00Z")

        var body = File.createTempFile("VIPV_", ".txt")
        val outputStreamWriter = body.outputStream().writer()
        SessionWriter.createJSONHeader(
            outputStreamWriter,
            TimeZone.getTimeZone("UTC"),
            beacons,
            startZonedDateTime,
            stopZonedDateTime,
            maxSessionTimeReached = false,
        )
        outputStreamWriter.flush()

        val expected = """
        {
          "version_scheme": ${SessionWriter.VERSION_SCHEME},
          "app_version": "${BuildConfig.VERSION_CODE}",
          "timezone": "UTC",
          "start_localized_instant": "2021-10-01T12:00:00Z",
          "finish_localized_instant": "2021-10-01T12:30:00Z",
          "device_info": {
            "manufacturer": "unknown",
            "model": "robolectric",
            "device": "robolectric",
            "android_version": "9",
            "sdk_int": 28
          },
          "wasMaxSessionTimeReached": false,
          "beacons": [
            {
              "id": "01:02:03:04:05:06",
              "tilt": 0.0,
              "position": "trunk",
              "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
            },
            {
              "id": "01:02:03:04:05:07",
              "tilt": 10.0,
              "position": "roof",
              "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
            }
          ]
        }
        """.replace("\n", "").replace(" ", "")

        val jsonHeaderFormatted = body.readText().replace("\n", "").replace(" ", "")

        assertEquals(expected, jsonHeaderFormatted)

        // Ensure all tasks on the main looper are executed
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun createCSVBody() {
        val beaconsCollectionRef = LoggingSession

        val beacons = beaconsCollectionRef.beacons.value!!

        // add two example beacons
        val beacon0 = BeaconSimplified("01:02:03:04:05:06")
        beacon0.setTilt(1.0f)
        beacon0.setPosition("trunk")
        beacon0.sensorData.value?.add(
            SensorEntry(
                Instant.parse("2021-10-01T12:00:00Z"),
                127,
                14.0f,
                15.0f,
                65.0f,
            )
        )
        beacon0.sensorData.value?.add(
            SensorEntry(
                Instant.parse("2021-10-01T12:00:01Z"),
                126,
                14.1f,
                15.1f,
                65.1f,
            )
        )
        beacon0.sensorData.value?.add(
            SensorEntry(
                Instant.parse("2021-10-01T12:00:02Z"),
                125,
                14.2f,
                15.2f,
                65.2f,
            )
        )
        beacons.add(beacon0)

        val beacon1 = BeaconSimplified("01:02:03:04:05:07")
        beacon1.setTilt(2.0f)
        beacon1.setPosition("roof")
        beacon1.sensorData.value?.add(
            SensorEntry(
                Instant.parse("2021-10-01T12:00:00Z"),
                127,
                14.0f,
                15.0f,
                65.0f,
            )
        )
        beacon1.sensorData.value?.add(
            SensorEntry(
                Instant.parse("2021-10-01T12:00:01Z"),
                128,
                14.1f,
                15.1f,
                65.1f,
            )
        )
        beacon1.sensorData.value?.add(
            SensorEntry(
                Instant.parse("2021-10-01T12:00:02Z"),
                129,
                14.2f,
                15.2f,
                65.2f,
            )
        )
        beacons.add(beacon1)

        val file = File.createTempFile("VIPV_", ".txt")
        val outputStreamWriter = file.outputStream().writer()
        SessionWriter.appendCsvHeader(outputStreamWriter)
        SessionWriter.appendCsvBodyFromData(outputStreamWriter, TimeZone.getTimeZone("UTC"), beacons)
        outputStreamWriter.flush()

        val expected = """
        beacon_id,localized_timestamp,data,latitude,longitude,azimuth
        01:02:03:04:05:06,12:00:00.000,127,14.0,15.0,65.0
        01:02:03:04:05:06,12:00:01.000,126,14.1,15.1,65.1
        01:02:03:04:05:06,12:00:02.000,125,14.2,15.2,65.2
        01:02:03:04:05:07,12:00:00.000,127,14.0,15.0,65.0
        01:02:03:04:05:07,12:00:01.000,128,14.1,15.1,65.1
        01:02:03:04:05:07,12:00:02.000,129,14.2,15.2,65.2
        
        """.trimIndent()  // remove leading whitespaces; last line is the trailing newline

        val csvBodyFormatted = file.readText().replace(" ", "")

        assertEquals(expected, csvBodyFormatted)
    }
}
