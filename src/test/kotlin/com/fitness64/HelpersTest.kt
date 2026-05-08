package com.fitness64

import com.fitness64.core.buildCyclingRecords
import com.fitness64.core.buildRunningRecords
import com.fitness64.core.formatClockDuration
import com.fitness64.core.formatDistance
import com.fitness64.core.formatSpeed
import com.fitness64.core.getStartOfWeek
import com.fitness64.core.loadActivityTypeNames
import com.fitness64.core.parseActivityDate
import com.fitness64.core.parseDistanceKm
import com.fitness64.core.parseDurationMinutes
import com.fitness64.core.pluralSuffix
import com.fitness64.core.splitPreferredActivities
import com.fitness64.core.workoutsForActivityType
import com.fitness64.schema.WorkoutLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.DayOfWeek
import java.time.LocalDate

class HelpersTest {

    @Test
    fun `parseDistanceKm returns null for blank input`() {
        assertNull(parseDistanceKm(""))
        assertNull(parseDistanceKm("  "))
    }

    @Test
    fun `parseDistanceKm returns null for non-numeric input`() {
        assertNull(parseDistanceKm("abc"))
    }

    @Test
    fun `parseDistanceKm parses plain km`() {
        assertEquals(10.0, parseDistanceKm("10"))
    }

    @Test
    fun `parseDistanceKm parses decimal km`() {
        assertEquals(10.5, parseDistanceKm("10.5"))
    }

    @Test
    fun `parseDistanceKm converts miles to km`() {
        val result = parseDistanceKm("10 miles")
        assertNotNull(result)
        assertEquals(10 * com.fitness64.core.KM_PER_MILE, result, 0.001)
    }

    @Test
    fun `parseDistanceKm converts mi with space to km`() {
        val result = parseDistanceKm("5 mi")
        assertNotNull(result)
        assertEquals(5 * com.fitness64.core.KM_PER_MILE, result, 0.001)
    }

    @Test
    fun `parseDurationMinutes returns null for blank input`() {
        assertNull(parseDurationMinutes(""))
        assertNull(parseDurationMinutes("  "))
    }

    @Test
    fun `parseDurationMinutes parses plain minutes`() {
        assertEquals(60, parseDurationMinutes("60"))
    }

    @Test
    fun `parseDurationMinutes parses HHMMSS`() {
        assertEquals(90, parseDurationMinutes("1:30:00"))
    }

    @Test
    fun `parseDurationMinutes rounds up seconds`() {
        assertEquals(31, parseDurationMinutes("0:30:30"))
    }

    @Test
    fun `parseDurationMinutes parses text hours and minutes`() {
        assertEquals(150, parseDurationMinutes("2h 30m"))
    }

    @Test
    fun `parseDurationMinutes parses hours text`() {
        assertEquals(120, parseDurationMinutes("2 hours"))
    }

    @Test
    fun `parseDurationMinutes parses minutes text`() {
        assertEquals(45, parseDurationMinutes("45 mins"))
    }

    @Test
    fun `formatClockDuration formats hours and minutes with seconds`() {
        assertEquals("1:30:00", formatClockDuration(90.0))
    }

    @Test
    fun `formatClockDuration formats only minutes`() {
        assertEquals("45:00", formatClockDuration(45.0))
    }

    @Test
    fun `formatClockDuration formats hours minutes and seconds`() {
        assertEquals("2:15:00", formatClockDuration(135.0))
    }

    @Test
    fun `formatClockDuration rounds seconds`() {
        assertEquals("2:30:30", formatClockDuration(150.5))
    }

    @Test
    fun `formatDistance displays integer without decimals`() {
        assertEquals("10", formatDistance(10.0))
    }

    @Test
    fun `formatDistance displays decimal with two significant digits`() {
        assertEquals("10.5", formatDistance(10.5))
    }

    @Test
    fun `formatDistance strips trailing zeros`() {
        assertEquals("10.5", formatDistance(10.50))
    }

    @Test
    fun `formatSpeed displays integer speed`() {
        assertEquals("10 km/h", formatSpeed(10.0))
    }

    @Test
    fun `formatSpeed displays decimal speed`() {
        assertEquals("10.5 km/h", formatSpeed(10.5))
    }

    @Test
    fun `splitPreferredActivities splits comma-separated string`() {
        assertEquals(listOf("Running", "Cycling"), splitPreferredActivities("Running,Cycling"))
    }

    @Test
    fun `splitPreferredActivities trims whitespace`() {
        assertEquals(listOf("Running", "Cycling"), splitPreferredActivities(" Running , Cycling "))
    }

    @Test
    fun `splitPreferredActivities returns empty list for null`() {
        assertTrue(splitPreferredActivities(null).isEmpty())
    }

    @Test
    fun `splitPreferredActivities returns empty list for blank`() {
        assertTrue(splitPreferredActivities("").isEmpty())
    }

    @Test
    fun `pluralSuffix returns empty for one`() {
        assertEquals("", pluralSuffix(1))
    }

    @Test
    fun `pluralSuffix returns s for zero`() {
        assertEquals("s", pluralSuffix(0))
    }

    @Test
    fun `pluralSuffix returns s for multiple`() {
        assertEquals("s", pluralSuffix(5))
    }

    @Test
    fun `getStartOfWeek returns Monday`() {
        val wednesday = LocalDate.of(2026, 5, 13)
        assertEquals(DayOfWeek.MONDAY, getStartOfWeek(wednesday).dayOfWeek)
        assertEquals(LocalDate.of(2026, 5, 11), getStartOfWeek(wednesday))
    }

    @Test
    fun `getStartOfWeek returns same day for Monday`() {
        val monday = LocalDate.of(2026, 5, 11)
        assertEquals(monday, getStartOfWeek(monday))
    }

    @Test
    fun `parseActivityDate parses valid ISO date`() {
        val result = parseActivityDate("2026-05-07")
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 5, 7), result)
    }

    @Test
    fun `parseActivityDate returns null for invalid date`() {
        assertNull(parseActivityDate("not-a-date"))
    }

    @Test
    fun `workoutsForActivityType filters by type name`() {
        val workouts = listOf(
            WorkoutLog(userId = 1, activityTypeId = 1, logDate = "2026-01-10", duration = 30, distance = 5.0),
            WorkoutLog(userId = 1, activityTypeId = 2, logDate = "2026-01-11", duration = 45, distance = 15.0)
        )
        val names = mapOf(1 to "Running", 2 to "Cycling")
        val result = workoutsForActivityType(workouts, names, "Running")
        assertEquals(1, result.size)
        assertEquals(5.0, result[0].distance)
    }

    @Test
    fun `workoutsForActivityType excludes zero distance workouts`() {
        val workouts = listOf(
            WorkoutLog(userId = 1, activityTypeId = 1, logDate = "2026-01-10", duration = 30, distance = 0.0),
            WorkoutLog(userId = 1, activityTypeId = 1, logDate = "2026-01-11", duration = 30, distance = 5.0)
        )
        val names = mapOf(1 to "Running")
        val result = workoutsForActivityType(workouts, names, "Running")
        assertEquals(1, result.size)
    }

    @Test
    fun `buildRunningRecords returns all expected records`() {
        val workouts = listOf(
            WorkoutLog(userId = 1, activityTypeId = 1, logDate = "2026-01-10", duration = 30, distance = 5.0),
            WorkoutLog(userId = 1, activityTypeId = 1, logDate = "2026-01-15", duration = 60, distance = 10.0)
        )
        val names = mapOf(1 to "Running")
        val records = buildRunningRecords(workouts, names)
        assertEquals(8, records.size)
        assertEquals("Fastest 1 km", records[0].label)
        assertEquals("Fastest 1 mile", records[1].label)
        assertEquals("Fastest 5 km", records[2].label)
        assertEquals("Fastest 10 km", records[3].label)
        assertEquals("Fastest half marathon", records[4].label)
        assertEquals("Fastest marathon", records[5].label)
        assertEquals("Longest run", records[6].label)
        assertEquals("Best pace", records[7].label)
    }

    @Test
    fun `buildRunningRecords shows no eligible run for insufficient distance`() {
        val workouts = listOf(
            WorkoutLog(userId = 1, activityTypeId = 1, logDate = "2026-01-10", duration = 10, distance = 0.5)
        )
        val names = mapOf(1 to "Running")
        val records = buildRunningRecords(workouts, names)
        assertTrue(records[0].value.contains("No eligible run"))
    }

    @Test
    fun `buildCyclingRecords returns expected records`() {
        val workouts = listOf(
            WorkoutLog(userId = 1, activityTypeId = 2, logDate = "2026-01-10", duration = 60, distance = 20.0)
        )
        val names = mapOf(2 to "Cycling")
        val records = buildCyclingRecords(workouts, names)
        assertEquals(2, records.size)
        assertEquals("Longest ride", records[0].label)
        assertEquals("Fastest average speed", records[1].label)
    }

    @Test
    fun `buildCyclingRecords shows no data message for empty input`() {
        val names = mapOf(2 to "Cycling")
        val records = buildCyclingRecords(emptyList(), names)
        assertTrue(records[0].value.contains("No cycling distance"))
        assertTrue(records[1].value.contains("No cycling speed"))
    }
}
