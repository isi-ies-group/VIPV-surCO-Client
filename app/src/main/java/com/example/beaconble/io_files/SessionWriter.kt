package com.example.beaconble.io_files

import com.example.beaconble.BeaconSimplified
import java.io.OutputStream
import java.util.Base64

object SessionWriter {
    /**
     * Dumps the data from the beacons to a custom-format file.
     * This file has the following format:
     *   - First lines is the JSON encoded list of beacons, with an ID field, tilt and orientation, and a UTF-8 description in Base64.
     *   - A newline separates the JSON line from the rest of the file.
     *   - The remaining lines is a CSV file with each SensorEntry field and, in the first column, the beacon ID.
     *     The fields are: beacon ID, timestamp, data, latitude, longitude
     *     No assumptions are made on the order of the entries.
     *   - File extension is recommended to be VIPV_${timestamp}.vipv_session
     * @param outFile The file to write to.
     * @param beacons The collection of beacons to dump.
     */
    fun dump2file(outFile: OutputStream, beacons: Collection<BeaconSimplified>) {
            outFile.writer(Charsets.UTF_8).use {
                // Write the JSON header.
                it.write(createJSONHeader(beacons))
                // Separate the JSON header from the rest of the file with a blank line.
                it.write("\n\n")
                // Write the CSV header.
                it.write("beacon_id,timestamp,data,latitude,longitude\n")
                // Write the CSV data.
                for (beacon in beacons) {
                    for (entry in beacon.sensorData.value!!) {
                        it.write(beacon.id.toString())
                        it.write(",")
                        it.write(entry.data.toString())
                        it.write(",")
                        it.write(entry.latitude.toString())
                        it.write(",")
                        it.write(entry.longitude.toString())
                        it.write(",")
                        it.write(entry.timestamp.toString())
                        it.write("\n")
                    }
                }
            }
    }

    /**
     * Creates the JSON line for the beacons static data (ID, tilt, orientation, description).
     * @param beacons The collection of beacons.
     * @return The JSON line String.
     *
     * Output example, formatted for readability:
     * {
     *  "beacons": {
     *    "id_of_beacon1": {
     *    "tilt": 0.0,
     *    "orientation": 0.0,
     *    "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
     *    },
     *    "id_of_beacon2": {
     *    "tilt": 0.0,
     *    "orientation": 0.0,
     *    "description": "U295IGxhIGNvc2l0YSBtw6FzIGxpbmRhIHkgbW9uYSBkZSBlc3RlIG11bmRvLg=="
     *    }
     *   }
     *  }
     */
    fun createJSONHeader(beacons: Collection<BeaconSimplified>): String {
        val result = StringBuilder()
        result.append("{")
        result.append("\"beacons\": {")
        for ((index, beacon) in beacons.withIndex()) {
            // Add a comma before the next element, as JSON does not allow trailing commas.
            // https://stackoverflow.com/questions/201782/can-you-use-a-trailing-comma-in-a-json-object
            if (index > 0) {
                result.append(",")
            }
            // ID identifier as key.
            result.append("\"${beacon.id}\": {")

            // Encode the description in Base64 to avoid issues with special characters (especially quotes and newlines).
            val base64encodedDescription =
                Base64.getEncoder().encodeToString(beacon.description.toByteArray(Charsets.UTF_8))
            result.append("\"tilt\": ${beacon.tilt},")
            result.append("\"orientation\": ${beacon.direction},")
            result.append("\"description\": \"$base64encodedDescription\"")

            result.append("}")
        }
        result.append("}")  // Close the "beacons" object.
        result.append("}")  // Close the JSON object.
        return result.toString()
    }
}
