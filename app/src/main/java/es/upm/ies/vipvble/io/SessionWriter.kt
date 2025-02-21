package es.upm.ies.vipvble.io

import es.upm.ies.vipvble.BeaconSimplified
import java.io.OutputStreamWriter
import java.time.Instant
import java.util.Base64

object SessionWriter {
    object V1 {

        /**
         * Creates the JSON line for the beacons static data (ID, tilt, orientation, description).
         * @param outputStreamWriter The output stream to write to.
         * @param beacons The collection of beacons.
         * @param startInstant The start instant of the session.
         * @param finishInstant The finish instant of the session.
         * @return The JSON line String.
         *
         * Output example, formatted for readability:
         * {
         *  "version_scheme": 1,
         *  "start_instant": "2021-10-01T12:00:00Z",
         *  "finish_instant": "2021-10-01T12:30:00Z",
         *  "beacons": [
         *    {
         *      "id": "id_of_beacon1",
         *      "tilt": 0.0,
         *      "orientation": 0.0,
         *      "position": "position_of_beacon1",
         *      "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
         *    },
         *    {
         *      "id": "id_of_beacon2",
         *      "tilt": 0.0,
         *      "orientation": 0.0,
         *      "position": "position_of_beacon2",
         *      "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
         *    }
         *   ]
         *  }
         */
        fun createJSONHeader(
            outputStreamWriter: OutputStreamWriter,
            beacons: Collection<BeaconSimplified>,
            startInstant: Instant,
            finishInstant: Instant,
        ) {
            outputStreamWriter.append("{")
            outputStreamWriter.append("\"version_scheme\": 1,")  // Version of the file format (this is the first version).
            outputStreamWriter.append("\"start_instant\": \"${startInstant}\",")
            outputStreamWriter.append("\"finish_instant\": \"${finishInstant}\",")
            outputStreamWriter.append("\"beacons\": [")  // Open "beacons"
            for ((index, beacon) in beacons.withIndex()) {
                // Add a comma before the next element, as JSON does not allow trailing commas.
                // https://stackoverflow.com/questions/201782/can-you-use-a-trailing-comma-in-a-json-object
                if (index > 0) {
                    outputStreamWriter.append(",")
                }
                outputStreamWriter.append("{")  // Open the unique beacon object.

                // Encode the description in Base64 to avoid issues with special characters (especially quotes and newlines).
                val base64encodedDescription =
                    Base64.getEncoder()
                        .encodeToString(beacon.descriptionValue.toByteArray(Charsets.UTF_8))
                outputStreamWriter.append("\"id\": \"${beacon.id}\",")
                outputStreamWriter.append("\"tilt\": ${beacon.tiltValue},")
                outputStreamWriter.append("\"orientation\": ${beacon.directionValue},")
                outputStreamWriter.append("\"position\": \"${beacon.positionValue}\",")
                outputStreamWriter.append("\"description\": \"$base64encodedDescription\"")

                outputStreamWriter.append("}")  // Close the unique beacon object.
            }
            outputStreamWriter.append("]")  // Close "beacons"
            outputStreamWriter.append("}")  // Close the JSON object.
        }

        /**
         * Create the CSV header of the file.
         * @param outputStreamWriter The output stream to write to.
         *
         * Currently, it is:
         * beacon_id,timestamp,data,latitude,longitude
         */
        fun appendCsvHeader(outputStreamWriter: OutputStreamWriter) {
            outputStreamWriter.write("beacon_id,timestamp,data,latitude,longitude\n")
        }

        /**
         * Create the CSV body of the file.
         * @param outputStreamWriter The output stream to write to.
         * @param beacons The collection of beacons.
         *
         * For example:
         * <header>
         * 0x010203040506,2021-10-01T12:00:00Z,127,0.0,0.0
         * 0x010203040506,2021-10-01T12:00:01Z,126,0.0,0.0
         * 0x010203040506,2021-10-01T12:00:02Z,125,0.0,0.0
         * 0x010203040507,2021-10-01T12:00:00Z,127,0.0,0.0
         * 0x010203040507,2021-10-01T12:00:01Z,128,0.0,0.0
         * 0x010203040507,2021-10-01T12:00:02Z,129,0.0,0.0
         */
        fun appendCsvBody(
            outputStreamWriter: OutputStreamWriter,
            beacons: Collection<BeaconSimplified>
        ) {
            for (beacon in beacons) {
                for (entry in beacon.sensorData.value!!) {
                    val result = StringBuilder()
                    result.append(beacon.id.toString())
                    result.append(",")
                    result.append(entry.timestamp.toString())
                    result.append(",")
                    result.append(entry.data.toString())
                    result.append(",")
                    result.append(entry.latitude.toString())
                    result.append(",")
                    result.append(entry.longitude.toString())
                    result.append("\n")
                    outputStreamWriter.write(result.toString())
                }
            }
        }

    }
}
