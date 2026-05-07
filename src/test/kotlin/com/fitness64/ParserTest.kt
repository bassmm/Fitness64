package com.fitness64

import com.fitness64.routes.activities.CsvParser
import com.fitness64.routes.activities.GpxParser
import com.fitness64.routes.activities.TcxParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserTest {

    private fun resource(name: String) =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "Test resource $name not found" }

    // ── CsvParser ──────────────────────────────────────────────────────────

    @Test
    fun csvParsesValidFile() {
        val result = CsvParser.parse(resource("test.csv"))
        assertEquals("2026-05-07", result.date)
        assertEquals("Running", result.activityType)
        assertEquals(1800, result.durationSeconds)
        assertEquals(5200.0, result.distanceMetres)
        assertEquals(300, result.calories)
        assertEquals("Morning run", result.notes)
    }

    @Test
    fun csvThrowsOnMissingRequiredColumn() {
        val csv = "date,duration_minutes\n2026-05-07,30\n".byteInputStream()
        assertFailsWith<IllegalArgumentException> { CsvParser.parse(csv) }
    }

    @Test
    fun csvThrowsOnHeaderOnly() {
        val csv = "date,duration_minutes,activity_type\n".byteInputStream()
        assertFailsWith<IllegalArgumentException> { CsvParser.parse(csv) }
    }

    @Test
    fun csvOptionalColumnsDefaultToNull() {
        val csv = "date,duration_minutes,activity_type\n2026-05-07,45,Cycling\n".byteInputStream()
        val result = CsvParser.parse(csv)
        assertNull(result.distanceMetres)
        assertNull(result.calories)
        assertEquals("", result.notes)
    }

    @Test
    fun csvConvertsDistanceKmToMetres() {
        val csv = "date,duration_minutes,activity_type,distance_km\n2026-05-07,30,Running,10.0\n".byteInputStream()
        val result = CsvParser.parse(csv)
        assertEquals(10000.0, result.distanceMetres)
    }

    // ── GpxParser ──────────────────────────────────────────────────────────

    @Test
    fun gpxParsesValidFile() {
        val result = GpxParser.parse(resource("test.gpx"))
        assertEquals(1, result.laps.size)
        val lap = result.laps.first()
        assertEquals(6, lap.trackpoints.size)
        assertEquals(1500, result.totalDuration)
    }

    @Test
    fun gpxCalculatesPositiveDistance() {
        val result = GpxParser.parse(resource("test.gpx"))
        assertTrue(result.totalDistance > 0, "Expected positive distance but got ${result.totalDistance}")
    }

    @Test
    fun gpxDistanceIsReasonable() {
        val result = GpxParser.parse(resource("test.gpx"))
        // 6 trackpoints across ~720 m (London area, haversine)
        assertTrue(result.totalDistance in 600.0..900.0, "Expected ~720 m but got ${result.totalDistance} m")
    }

    @Test
    fun gpxTrackpointsHaveCoordinates() {
        val result = GpxParser.parse(resource("test.gpx"))
        val trackpoints = result.laps.first().trackpoints
        assertTrue(trackpoints.all { it.latitude != null && it.longitude != null })
    }

    @Test
    fun gpxReturnsNullCalories() {
        val result = GpxParser.parse(resource("test.gpx"))
        assertNull(result.totalCalories)
    }

    // ── TcxParser ──────────────────────────────────────────────────────────

    @Test
    fun tcxParsesValidFile() {
        val result = TcxParser.parse(resource("test.tcx"))
        assertEquals(2, result.laps.size)
    }

    @Test
    fun tcxTotalsDurationAcrossLaps() {
        val result = TcxParser.parse(resource("test.tcx"))
        assertEquals(1200, result.totalDuration)
    }

    @Test
    fun tcxTotalsDistanceAcrossLaps() {
        val result = TcxParser.parse(resource("test.tcx"))
        assertEquals(3000.0, result.totalDistance)
    }

    @Test
    fun tcxTotalsCaloriesAcrossLaps() {
        val result = TcxParser.parse(resource("test.tcx"))
        assertNotNull(result.totalCalories)
        assertEquals(250, result.totalCalories)
    }

    @Test
    fun tcxParsesTrackpoints() {
        val result = TcxParser.parse(resource("test.tcx"))
        val allTrackpoints = result.laps.flatMap { it.trackpoints }
        assertEquals(5, allTrackpoints.size)
        assertTrue(allTrackpoints.all { it.latitude != null && it.longitude != null })
    }

    @Test
    fun tcxParsesHeartRate() {
        val result = TcxParser.parse(resource("test.tcx"))
        val heartRates = result.laps.flatMap { it.trackpoints }.mapNotNull { it.heartRate }
        assertTrue(heartRates.isNotEmpty())
        assertEquals(140, heartRates.first())
    }
}
