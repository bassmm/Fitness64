package com.fitness64.routes.activities

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivitySearchTest {

    @Test
    fun `blank search matches activity`() {
        assertTrue(
            activityMatchesSearch(
                query = "",
                title = "Morning Run",
                type = "cardio",
                category = "Running",
                date = "2026-01-10"
            )
        )
    }

    @Test
    fun `search matches activity title case insensitively`() {
        assertTrue(
            activityMatchesSearch(
                query = "morning",
                title = "Morning Run",
                type = "cardio",
                category = "Running",
                date = "2026-01-10"
            )
        )
    }

    @Test
    fun `search matches activity type`() {
        assertTrue(
            activityMatchesSearch(
                query = "race",
                title = "London Marathon",
                type = "race",
                category = "Race",
                date = "2026-04-12"
            )
        )
    }

    @Test
    fun `search matches activity category`() {
        assertTrue(
            activityMatchesSearch(
                query = "running",
                title = "Morning Session",
                type = "cardio",
                category = "Running",
                date = "2026-01-10"
            )
        )
    }

    @Test
    fun `search matches activity date`() {
        assertTrue(
            activityMatchesSearch(
                query = "2026-01",
                title = "Morning Run",
                type = "cardio",
                category = "Running",
                date = "2026-01-10"
            )
        )
    }

    @Test
    fun `search does not match unrelated activity`() {
        assertFalse(
            activityMatchesSearch(
                query = "swimming",
                title = "Morning Run",
                type = "cardio",
                category = "Running",
                date = "2026-01-10"
            )
        )
    }
}
