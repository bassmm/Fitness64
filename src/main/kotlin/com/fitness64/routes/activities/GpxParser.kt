package com.fitness64.routes.activities

import org.w3c.dom.Element
import java.io.InputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.*

object GpxParser {

    fun parse(input: InputStream): ParsedTcxData {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(input)
        document.documentElement.normalize()

        val trkptElements = document.getElementsByTagName("trkpt")
        val trackpoints = mutableListOf<ParsedTrackpoint>()

        for (i in 0 until trkptElements.length) {
            val tp = trkptElements.item(i) as Element
            val lat = tp.getAttribute("lat").toDoubleOrNull()
            val lon = tp.getAttribute("lon").toDoubleOrNull()
            val ele = getElementText(tp, "ele")?.toDoubleOrNull()
            val time = getElementText(tp, "time") ?: ""
            val hr = getElementText(tp, "gpxtpx:hr")?.toIntOrNull()
                ?: getElementText(tp, "hr")?.toIntOrNull()

            trackpoints.add(
                ParsedTrackpoint(
                    time = time,
                    latitude = lat,
                    longitude = lon,
                    altitude = ele,
                    distance = null,
                    heartRate = hr
                )
            )
        }

        val totalDistanceMetres = calculateTotalDistance(trackpoints)

        val totalDurationSeconds = if (trackpoints.size >= 2) {
            val first = trackpoints.first().time
            val last = trackpoints.last().time
            runCatching {
                (Instant.parse(last).epochSecond - Instant.parse(first).epochSecond).toInt()
                    .coerceAtLeast(0)
            }.getOrDefault(0)
        } else 0

        val lap = ParsedLap(
            startTime = trackpoints.firstOrNull()?.time ?: "",
            totalTimeSeconds = totalDurationSeconds,
            distance = totalDistanceMetres,
            calories = null,
            trackpoints = trackpoints
        )

        return ParsedTcxData(
            totalDuration = totalDurationSeconds,
            totalDistance = totalDistanceMetres,
            totalCalories = null,
            laps = listOf(lap)
        )
    }

    private fun calculateTotalDistance(trackpoints: List<ParsedTrackpoint>): Double {
        var total = 0.0
        for (i in 1 until trackpoints.size) {
            val a = trackpoints[i - 1]
            val b = trackpoints[i]
            if (a.latitude != null && a.longitude != null && b.latitude != null && b.longitude != null) {
                total += haversineMetres(a.latitude, a.longitude, b.latitude, b.longitude)
            }
        }
        return total
    }

    private fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun getElementText(parent: Element, tagName: String): String? {
        val elements = parent.getElementsByTagName(tagName)
        if (elements.length == 0) return null
        return elements.item(0)?.textContent?.trim()
    }
}
