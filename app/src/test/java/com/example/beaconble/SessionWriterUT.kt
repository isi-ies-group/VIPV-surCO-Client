package com.example.beaconble

import com.example.beaconble.io_files.SessionWriter
import org.altbeacon.beacon.Identifier
import org.junit.Test

class SessionWriterUT {
    @Test
    fun createJSONHeader() {
        val beaconsCollectionRef = BeaconsCollection()

        val beacons = beaconsCollectionRef.beacons.value!!

        // add two example beacons
        val beacon1 = BeaconSimplified(Identifier.parse("0x010203040506"))
        beacon1.tilt = 0.0f
        beacon1.direction = 0.0f
        beacon1.description = "Soy la cosita más linda y mona de este mundo."
        beacons.add(beacon1)

        val beacon2 = BeaconSimplified(Identifier.parse("0x010203040507"))
        beacon2.tilt = 10.0f
        beacon2.direction = 180.0f
        beacon2.description = "Soy la cosita más linda y mona de este mundo."
        beacons.add(beacon2)

        val jsonHeader = SessionWriter.createJSONHeader(beacons)

        val expected = """
        {
        "beacons": {
          "0x010203040506": {
          "tilt": 0.0,
          "orientation": 0.0,
          "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
          },
          "0x010203040507": {
          "tilt": 10.0,
          "orientation": 180.0,
          "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
          }
         }
        }
        """.replace("\n", "").replace(" ", "")

        val jsonHeaderFormatted = jsonHeader.replace(" ", "")

        assert(jsonHeaderFormatted == expected)
    }
}
