package com.fitness64.routes.activities

import java.io.InputStream

data class ParsedCsvActivity(
    val date: String,
    val durationSeconds: Int,
    val distanceMetres: Double?,
    val calories: Int?,
    val activityType: String,
    val notes: String
)

object CsvParser {

    private val REQUIRED_HEADERS = setOf("date", "duration_minutes", "activity_type")

    fun parse(input: InputStream): ParsedCsvActivity {
        val lines = input.bufferedReader().readLines().filter { it.isNotBlank() }
        require(lines.size >= 2) { "CSV must have a header row and at least one data row." }

        val headers = lines[0].split(",").map { it.trim().lowercase() }
        val missing = REQUIRED_HEADERS - headers.toSet()
        require(missing.isEmpty()) { "CSV is missing required columns: ${missing.joinToString()}." }

        val values = lines[1].split(",").map { it.trim() }
        fun col(name: String): String? = headers.indexOf(name).takeIf { it >= 0 }?.let { values.getOrNull(it) }?.ifBlank { null }

        val date = col("date") ?: throw IllegalArgumentException("Missing value for 'date'.")
        val durationMinutes = col("duration_minutes")?.toDoubleOrNull()
            ?: throw IllegalArgumentException("Missing or invalid value for 'duration_minutes'.")
        val activityType = col("activity_type") ?: throw IllegalArgumentException("Missing value for 'activity_type'.")

        return ParsedCsvActivity(
            date = date,
            durationSeconds = (durationMinutes * 60).toInt(),
            distanceMetres = col("distance_km")?.toDoubleOrNull()?.let { it * 1000 },
            calories = col("calories")?.toIntOrNull(),
            activityType = activityType,
            notes = col("notes") ?: ""
        )
    }
}
