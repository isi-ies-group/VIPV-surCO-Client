package com.example.beaconble.io_files

import com.example.beaconble.BeaconSimplified
import com.example.beaconble.LoggingSession
import java.io.OutputStream
import java.util.Base64

object SessionWriter {
    object V1 {
        /**
         * Dumps the data from the beacons to a custom-format file.
         * This file has the following format:
         *   - First lines is the JSON encoded:
         *     * version scheme
         *     * start instant
         *     * finish instant
         *     * indexed list of beacons
         *       + with an ID field
         *       + tilt
         *       + orientation
         *       + UTF-8 description in Base64.
         *   - A newline separates the JSON line from the rest of the file.
         *   - The remaining lines is a CSV file with each SensorEntry field and, in the first column, the beacon ID.
         *     The fields are:
         *     * beacon index (from the beacons list in the header)
         *     * timestamp
         *     * data
         *     * latitude
         *     * longitude
         *     No assumptions are made on the order of the entries.
         *   - File name is recommended to be VIPV_${timestamp}.txt
         * @param outFile The file to write to.
         * @param loggingSession The session of beacons to dump.
         */
        fun dump2file(outFile: OutputStream, loggingSession: LoggingSession) {
            val beacons = loggingSession.beacons.value!!
            outFile.writer(Charsets.UTF_8).use {
                // Write the JSON header.
                it.write(createJSONHeader(beacons))
                // Separate the JSON header from the rest of the file with a blank line.
                it.write("\n\n")
                // Write the CSV part.
                it.write(createCSVBody(beacons))
            }
        }

        /**
         * Creates the JSON line for the beacons static data (ID, tilt, orientation, description).
         * @param beacons The collection of beacons.
         * @return The JSON line String.
         *
         * Output example, formatted for readability:
         * {
         *  "version_scheme": 1,
         *  "start_instant": "2021-10-01T12:00:00Z",
         *  "finish_instant": "2021-10-01T12:30:00Z",
         *  "beacons": {
         *    "0": {
         *      "id": "id_of_beacon1",
         *      "tilt": 0.0,
         *      "orientation": 0.0,
         *      "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
         *    },
         *    "1": {
         *      "id": "id_of_beacon2",
         *      "tilt": 0.0,
         *      "orientation": 0.0,
         *      "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
         *    }
         *   }
         *  }
         */
        fun createJSONHeader(beacons: Collection<BeaconSimplified>): String {
            val result = StringBuilder()
            result.append("{")
            result.append("\"version_scheme\": 1,")  // Version of the file format (this is the first version).
            result.append("\"start_instant\": \"${LoggingSession.startInstant}\",")
            result.append("\"finish_instant\": \"${LoggingSession.stopInstant}\",")
            result.append("\"beacons\": {")  // Open "beacons"
            for ((index, beacon) in beacons.withIndex()) {
                // Add a comma before the next element, as JSON does not allow trailing commas.
                // https://stackoverflow.com/questions/201782/can-you-use-a-trailing-comma-in-a-json-object
                if (index > 0) {
                    result.append(",")
                }
                // ID identifier as beacon index in the list.
                result.append("\"${index}\": {")

                // Encode the description in Base64 to avoid issues with special characters (especially quotes and newlines).
                val base64encodedDescription =
                    Base64.getEncoder()
                        .encodeToString(beacon.description.toByteArray(Charsets.UTF_8))
                result.append("\"id\": \"${beacon.id}\",")
                result.append("\"tilt\": ${beacon.tilt},")
                result.append("\"orientation\": ${beacon.direction},")
                result.append("\"description\": \"$base64encodedDescription\"")

                result.append("}")
            }
            result.append("}")  // Close "beacons"
            result.append("}")  // Close the JSON object.
            return result.toString()
        }

        /**
         * Create the CSV header and body of the file.
         * @param beacons The collection of beacons.
         */
        fun createCSVBody(beacons: Collection<BeaconSimplified>): String {
            val result = StringBuilder()
            result.append("beacon_index,timestamp,data,latitude,longitude\n")
            for ((index, beacon) in beacons.withIndex()) {
                for (entry in beacon.sensorData.value!!) {
                    result.append(index.toString())
                    result.append(",")
                    result.append(entry.timestamp.toString())
                    result.append(",")
                    result.append(entry.data.toString())
                    result.append(",")
                    result.append(entry.latitude.toString())
                    result.append(",")
                    result.append(entry.longitude.toString())
                    result.append("\n")
                }
            }
            return result.toString()
        }
    }
}
