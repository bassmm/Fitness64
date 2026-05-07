# Fitness64

A full-featured fitness tracking web application built with **Kotlin** and **Ktor**. Log workouts, track progress, import GPS routes from fitness devices, manage training plans, and log race results — all in one place.

---

## Features

- **User Accounts** — Register, log in, and manage your profile with fitness level, goals, and preferences
- **Activity Logging** — Log cardio (Running, Cycling, Swimming) with distance, duration, and calories; log weightlifting sessions with multi-exercise sets, reps, and weight
- **TCX Import** — Upload Garmin/Strava TCX files to extract GPS routes, heart rate data, altitude, and lap information
- **Route Map** — Leaflet.js multi-lap colored polylines with start/finish markers on activity detail pages
- **Heart Rate Chart** — Chart.js line graph for cardio activities with imported heart rate data
- **Training Plans** — Auto-generated weekly plans (cardio, weightlifting, beginner, custom) with per-day session editing
- **Calendar View** — See all logged activities and planned sessions on a monthly calendar
- **Progress Dashboard** — Consistency streak, running records (5K/10K/half/full marathon pace estimates), cycling records, activity breakdown charts
- **Race Logging** — Log race results with event name, location, category, finish time, rankings, and personal best tracking
- **Activity History** — Unified feed of all cardio, weightlifting, and race entries with inline HTMX editing
- **HTMX-powered UI** — Fast partial-page updates, inline editing, and toast notifications without a JavaScript framework

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | [Kotlin](https://kotlinlang.org/) 2.3.0 (JVM 25) |
| Web Framework | [Ktor](https://ktor.io/) 3.4.0 (Netty) |
| ORM | [JetBrains Exposed](https://github.com/JetBrains/Exposed) 1.0.0 |
| Database | SQLite 3.45 (production) / H2 2.3 (testing) |
| Templating | [Pebble Templates](https://pebbletemplates.io/) |
| Auth | Cookie-based sessions, BCrypt password hashing |
| Frontend | [HTMX](https://htmx.org/), [Bootstrap 5](https://getbootstrap.com/) (via [Webpixels CSS](https://webpixels.io/)), [Chart.js](https://www.chartjs.org/), [Leaflet.js](https://leafletjs.com/) |
| Serialization | kotlinx.serialization (JSON) |
| Build | Gradle (Kotlin DSL) |

---

## Architecture

```
src/main/kotlin/com/fitness64/
├── core/          # Infrastructure: app wiring, DB setup, auth, templates, utilities
├── routes/        # Controllers / route handlers (flat, except activities/)
│   └── activities/  # Activity feed, detail, edit, and TCX import
└── schema/        # Data layer: Exposed tables, DAOs, services
```

- **core/** — `Application.kt` (entry point), `Databases.kt`, `Security.kt` (cookie sessions + auth providers), `Templating.kt` (Pebble + HTMX helpers), `Helpers.kt`, `Serialization.kt`
- **routes/** — `AuthRoutes.kt`, `DashboardRoutes.kt`, `LogRoutes.kt`, `CalendarRoutes.kt`, `PlanRoutes.kt`, `ProgressRoutes.kt`, `RacesPagesRoutes.kt`, and `activities/` sub-package
- **schema/** — `UsersSchema.kt`, `ActivitySchema.kt`, `WeightliftingSchema.kt`, `PlanSchema.kt`, `RaceSchema.kt`

Templates live in `src/main/resources/templates/` with HTMX partials in `_partials/`.

---

## Quick Start

```bash
./gradlew run
```

Go to [http://localhost:8080/](http://localhost:8080/).

### Available Tasks

| Task | Description |
|---|---|
| `./gradlew run` | Start the development server |
| `./gradlew test` | Run tests |
| `./gradlew build` | Build the project |
| `./gradlew buildFatJar` | Build an executable fat JAR |

---

## Database Schema

The application creates the following tables automatically on startup:

| Table | Purpose |
|---|---|
| `users` | User accounts with BCrypt-hashed passwords, fitness level, goals |
| `activity_types` | Lookup table (Running, Cycling, Swimming, Weightlifting) |
| `workout_logs` | Cardio activity records with date, duration, distance, calories |
| `workout_laps` | Lap data imported from TCX files |
| `trackpoints` | GPS coordinates, heart rate, altitude from TCX |
| `workout_exercises` | Exercises linked to weightlifting logs |
| `weightlifting_workout_logs` | Weightlifting session records |
| `weightlifting_session_exercises` | Per-exercise sets, reps, weight in a weightlifting session |
| `training_plans` | Weekly training plans (type, start date) |
| `plan_sessions` | Individual planned sessions within a plan |
| `race_records` | Logged race results with rankings and personal bests |

A DBML diagram is available at `src/main/resources/db/schema.dbml`.

---

## Routes Overview

| Method | Path | Description |
|---|---|---|
| GET/POST | `/login` | Login form and authentication |
| GET/POST | `/register` | Registration form and account creation |
| GET | `/logout` | Clear session, redirect to login |
| GET | `/home` | Dashboard with today's plan, streak, goals |
| GET | `/profile` | Profile view and edit |
| GET | `/log` | Log a new activity (cardio or weightlifting) |
| POST | `/log/submit` | Submit a logged workout |
| GET | `/activities` | Unified activity history feed |
| GET | `/activities/{id}` | Activity detail with map and heart rate chart |
| GET/POST | `/activities/{id}/edit` | Inline activity editing |
| GET | `/calendar` | Monthly activity + plan calendar |
| GET/POST | `/plan` | View and manage weekly training plan |
| GET/POST | `/onboarding` | New-user plan selection |
| GET | `/progress` | Progress dashboard with records and charts |
| GET | `/races` | Race history listing |
| GET/POST | `/races/log` | Log a new race result |
| GET/POST | `/tcx/upload` | Upload and import a TCX file |
