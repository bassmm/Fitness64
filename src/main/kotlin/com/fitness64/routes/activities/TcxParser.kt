package com.fitness64.routes.activities

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory
import java.io.InputStream

data class ParsedTcxData(
    val totalDuration: Int,
    val totalDistance: Double,
    val totalCalories: Int?,
    val laps: List<ParsedLap>
)

data class ParsedLap(
    val startTime: String,
    val totalTimeSeconds: Int,
    val distance: Double,
    val calories: Int?,
    val trackpoints: List<ParsedTrackpoint>
)

data class ParsedTrackpoint(
    val time: String,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val distance: Double?,
    val heartRate: Int?
)

object TcxParser {

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

    private fun getElementText(parent: Element, tagName: String): String? {
        val elements = parent.getElementsByTagName(tagName)
        if (elements.length == 0) return null
        return elements.item(0)?.textContent?.trim()
    }
}
