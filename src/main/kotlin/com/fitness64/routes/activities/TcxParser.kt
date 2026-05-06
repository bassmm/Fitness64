/**
 * TcxParser.kt
 *
 * Parses TCX (Training Center XML) files exported from fitness devices
 * such as Garmin watches, Strava, and other GPS-enabled fitness trackers.
 * Extracts lap and trackpoint data including GPS coordinates, heart rate,
 * altitude, and distance for storage in the database.
 */
package com.fitness64.routes.activities

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import java.io.InputStream

/**
 * Represents the complete parsed result of a TCX file.
 *
 * @property totalDuration Total workout duration in seconds across all laps.
 * @property totalDistance Total distance in metres across all laps.
 * @property totalCalories Total calories burned, or null if not recorded.
 * @property laps List of individual laps parsed from the TCX file.
 */
data class ParsedTcxData(
    val totalDuration: Int,
    val totalDistance: Double,
    val totalCalories: Int?,
    val laps: List<ParsedLap>
)

/**
 * Represents a single lap within a TCX activity.
 *
 * @property startTime The ISO timestamp when the lap started.
 * @property totalTimeSeconds Total duration of the lap in seconds.
 * @property distance Total distance covered in this lap in metres.
 * @property calories Calories burned during this lap, or null if not recorded.
 * @property trackpoints List of GPS trackpoints recorded during this lap.
 */
data class ParsedLap(
    val startTime: String,
    val totalTimeSeconds: Int,
    val distance: Double,
    val calories: Int?,
    val trackpoints: List<ParsedTrackpoint>
)

/**
 * Represents a single GPS trackpoint recorded during a lap.
 *
 * @property time The ISO timestamp of this trackpoint.
 * @property latitude GPS latitude in decimal degrees, or null if not recorded.
 * @property longitude GPS longitude in decimal degrees, or null if not recorded.
 * @property altitude Altitude in metres, or null if not recorded.
 * @property distance Cumulative distance in metres at this point, or null if not recorded.
 * @property heartRate Heart rate in BPM at this point, or null if not recorded.
 */
data class ParsedTrackpoint(
    val time: String,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val distance: Double?,
    val heartRate: Int?
)

/**
 * Singleton object responsible for parsing TCX XML files into structured data.
 * Uses Java's built-in XML DOM parser to read the TCX format.
 */
object TcxParser {

    /**
     * Parses a TCX file from the given input stream and extracts all
     * lap and trackpoint data into a [ParsedTcxData] object.
     *
     * @param input The input stream of the TCX file to parse.
     * @return A [ParsedTcxData] object containing all extracted workout data.
     */
    fun parse(input: InputStream): ParsedTcxData {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(input)
        document.documentElement.normalize()

        val lapElements = document.getElementsByTagName("Lap")
        val parsedLaps = mutableListOf<ParsedLap>()

        var totalDuration = 0
        var totalDistance = 0.0
        var totalCalories = 0

        for (i in 0 until lapElements.length) {
            val lapElement = lapElements.item(i) as Element

            val startTime = lapElement.getAttribute("StartTime") ?: ""
            val totalTimeSeconds = getElementText(lapElement, "TotalTimeSeconds")?.toDoubleOrNull()?.toInt() ?: 0
            val distance = getElementText(lapElement, "DistanceMeters")?.toDoubleOrNull() ?: 0.0
            val calories = getElementText(lapElement, "Calories")?.toIntOrNull()

            totalDuration += totalTimeSeconds
            totalDistance += distance
            totalCalories += calories ?: 0

            val trackpointElements = lapElement.getElementsByTagName("Trackpoint")
            val parsedTrackpoints = mutableListOf<ParsedTrackpoint>()

            for (j in 0 until trackpointElements.length) {
                val tp = trackpointElements.item(j) as Element

                val time = getElementText(tp, "Time") ?: ""
                val latitude = getElementText(tp, "LatitudeDegrees")?.toDoubleOrNull()
                val longitude = getElementText(tp, "LongitudeDegrees")?.toDoubleOrNull()
                val altitude = getElementText(tp, "AltitudeMeters")?.toDoubleOrNull()
                val tpDistance = getElementText(tp, "DistanceMeters")?.toDoubleOrNull()
                val heartRate = getElementText(tp, "Value")?.toIntOrNull()

                parsedTrackpoints.add(
                    ParsedTrackpoint(
                        time = time,
                        latitude = latitude,
                        longitude = longitude,
                        altitude = altitude,
                        distance = tpDistance,
                        heartRate = heartRate
                    )
                )
            }

            parsedLaps.add(
                ParsedLap(
                    startTime = startTime,
                    totalTimeSeconds = totalTimeSeconds,
                    distance = distance,
                    calories = calories,
                    trackpoints = parsedTrackpoints
                )
            )
        }

        return ParsedTcxData(
            totalDuration = totalDuration,
            totalDistance = totalDistance,
            totalCalories = if (totalCalories > 0) totalCalories else null,
            laps = parsedLaps
        )
    }

    /**
     * Retrieves the text content of the first child element matching the given tag name.
     *
     * @param parent The parent XML element to search within.
     * @param tagName The tag name of the child element to find.
     * @return The trimmed text content of the element, or null if not found.
     */
    private fun getElementText(parent: Element, tagName: String): String? {
        val elements = parent.getElementsByTagName(tagName)
        if (elements.length == 0) return null
        return elements.item(0)?.textContent?.trim()
    }
}
