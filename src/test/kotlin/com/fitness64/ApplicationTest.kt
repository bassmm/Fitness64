/**
 * ApplicationTest.kt
 *
 * Integration tests for the Fitness64 application.
 * Tests cover application startup, public page availability,
 * protected route access control, and form validation for
 * registration and login flows.
 *
 * Uses an in-memory H2 database for test isolation.
 */
package com.fitness64

import com.fitness64.core.module
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ApplicationTest {

    /**
     * Sets the test.database system property before each test to ensure
     * the application uses an in-memory H2 database instead of SQLite.
     */
    @BeforeTest
    fun setup() {
        System.setProperty("test.database", "true")
    }

    // ==================== Application Startup ====================

    /**
     * Verifies that the application starts without throwing any exceptions.
     */
    @Test
    fun testApplicationStarts() = testApplication {
        application { module() }
    }

    // ==================== Public Pages ====================

    /**
     * Verifies that the login page is publicly accessible and returns 200 OK.
     */
    @Test
    fun testLoginPageLoads() = testApplication {
        application { module() }
        val response = client.get("/login")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    /**
     * Verifies that the register page is publicly accessible and returns 200 OK.
     */
    @Test
    fun testRegisterPageLoads() = testApplication {
        application { module() }
        val response = client.get("/register")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    /**
     * Verifies that the root path redirects or responds successfully.
     */
    @Test
    fun testRootResponds() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/")
        assertTrue(
            response.status == HttpStatusCode.Found ||
            response.status == HttpStatusCode.OK
        )
    }

    // ==================== Protected Route Redirects ====================

    /**
     * Verifies that /home redirects unauthenticated users to /login.
     */
    @Test
    fun testHomeRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/home")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /activities redirects unauthenticated users to /login.
     */
    @Test
    fun testActivitiesRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/activities")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /progress redirects unauthenticated users to /login.
     */
    @Test
    fun testProgressRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/progress")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /races/log redirects unauthenticated users to /login.
     */
    @Test
    fun testRaceLogRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/races/log")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /profile redirects unauthenticated users to /login.
     */
    @Test
    fun testProfileRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/profile")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /tcx/upload redirects unauthenticated users to /login.
     */
    @Test
    fun testTcxUploadRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/tcx/upload")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /log redirects unauthenticated users to /login.
     */
    @Test
    fun testLogRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/log")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /onboarding redirects unauthenticated users to /login.
     */
    @Test
    fun testOnboardingRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/onboarding")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    // ==================== Registration Validation ====================

    /**
     * Verifies that submitting blank registration fields re-renders
     * the form with an error message rather than creating a user.
     */
    @Test
    fun testRegisterWithBlankFieldsShowsError() = testApplication {
        application { module() }
        val response = client.submitForm(
            url = "/register",
            formParameters = parameters {
                append("name", "")
                append("email", "")
                append("password", "")
                append("fitnessLevel", "")
            }
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "required")
    }

    /**
     * Verifies that a valid registration form submission redirects to /login.
     */
    @Test
    fun testRegisterWithValidDataRedirectsToLogin() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.submitForm(
            url = "/register",
            formParameters = parameters {
                append("name", "Test User")
                append("email", "test@example.com")
                append("password", "password123")
                append("fitnessLevel", "Beginner")
            }
        )
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers[HttpHeaders.Location])
    }

    /**
     * Verifies that attempting to register with an already registered email
     * re-renders the form with an appropriate error message.
     */
    @Test
    fun testRegisterWithDuplicateEmailShowsError() = testApplication {
        application { module() }
        client.submitForm(
            url = "/register",
            formParameters = parameters {
                append("name", "Test User")
                append("email", "duplicate@example.com")
                append("password", "password123")
                append("fitnessLevel", "Beginner")
            }
        )
        val response = client.submitForm(
            url = "/register",
            formParameters = parameters {
                append("name", "Another User")
                append("email", "duplicate@example.com")
                append("password", "password123")
                append("fitnessLevel", "Beginner")
            }
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "already exists")
    }

    // ==================== Login Validation ====================

    /**
     * Verifies that submitting blank login fields re-renders the form with an error.
     */
    @Test
    fun testLoginWithBlankFieldsShowsError() = testApplication {
        application { module() }
        val response = client.submitForm(
            url = "/login",
            formParameters = parameters {
                append("email", "")
                append("password", "")
            }
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "enter both")
    }

    /**
     * Verifies that logging in with invalid credentials re-renders
     * the form with an error message.
     */
    @Test
    fun testLoginWithInvalidCredentialsShowsError() = testApplication {
        application { module() }
        val response = client.submitForm(
            url = "/login",
            formParameters = parameters {
                append("email", "nonexistent@example.com")
                append("password", "wrongpassword")
            }
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Invalid")
    }

    /**
     * Verifies that logging in with valid credentials redirects the user.
     * Registers a user first to ensure valid credentials exist.
     */
    @Test
    fun testLoginWithValidCredentialsRedirects() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        noRedirectClient.submitForm(
            url = "/register",
            formParameters = parameters {
                append("name", "Login Test User")
                append("email", "logintest@example.com")
                append("password", "password123")
                append("fitnessLevel", "Beginner")
            }
        )
        val response = noRedirectClient.submitForm(
            url = "/login",
            formParameters = parameters {
                append("email", "logintest@example.com")
                append("password", "password123")
            }
        )
        assertEquals(HttpStatusCode.Found, response.status)
    }

    // ==================== Form POST Protection ====================

    /**
     * Verifies that posting to the race log endpoint redirects unauthenticated users.
     */
    @Test
    fun testRaceLogPostRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.submitForm(
            url = "/races/log",
            formParameters = parameters {
                append("eventName", "Leeds 10K")
                append("eventDate", "2026-05-11")
            }
        )
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that posting to the import endpoint redirects unauthenticated users.
     */
    @Test
    fun testImportPostRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.post("/import")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that posting to the log submit endpoint redirects unauthenticated users.
     */
    @Test
    fun testLogSubmitPostRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.submitForm(
            url = "/log/submit",
            formParameters = parameters {
                append("duration", "60")
                append("logDate", "2026-05-06")
            }
        )
        assertEquals(HttpStatusCode.Found, response.status)
    }

    // ==================== Authentication Helpers ====================

    private suspend fun registerAndLogin(client: HttpClient, email: String): String {
        client.submitForm("/register", formParameters = parameters {
            append("name", "Test User")
            append("email", email)
            append("password", "password123")
            append("fitnessLevel", "Intermediate")
        })
        val loginResp = client.submitForm("/login", formParameters = parameters {
            append("email", email)
            append("password", "password123")
        })
        val setCookie = loginResp.headers["Set-Cookie"] ?: ""
        return setCookie.substringBefore(";")
    }

    // ==================== Authenticated Page Rendering ====================

    @Test
    fun testDashboardLoadsWhenAuthenticated() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "dash-render@test.com")
        val response = client.get("/home") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Start your weekly streak")
    }

    @Test
    fun testProfileLoadsWhenAuthenticated() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "profile-render@test.com")
        val response = client.get("/profile") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Test User")
    }

    @Test
    fun testLogPageLoadsWhenAuthenticated() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "log-render@test.com")
        val response = client.get("/log") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testActivitiesPageLoadsWhenAuthenticated() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "activities-render@test.com")
        val response = client.get("/activities") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testProgressPageLoadsWhenAuthenticated() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "progress-render@test.com")
        val response = client.get("/progress") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testOnboardingPageLoadsWhenAuthenticated() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "onboard-render@test.com")
        val response = client.get("/onboarding") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Choose Training Plan")
    }

    @Test
    fun testRaceLogPageLoadsWhenAuthenticated() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "races-render@test.com")
        val response = client.get("/races/log") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testImportPageLoadsWhenAuthenticated() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "import-render@test.com")
        val response = client.get("/import") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ==================== Auth Flow ====================

    @Test
    fun testLogoutRedirectsToLogin() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "logout-flow@test.com")
        val response = client.get("/logout") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers[HttpHeaders.Location])
    }

    // ==================== HTMX Partial Endpoints ====================

    @Test
    fun testProfileViewHtmxEndpoint() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "profile-view@test.com")
        val response = client.get("/profile/view") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Test User")
    }

    @Test
    fun testProfileEditFormHtmxEndpoint() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "profile-edit@test.com")
        val response = client.get("/profile/edit-form") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testLogPickerHtmxEndpoint() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "log-picker@test.com")
        val response = client.get("/log/picker") { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testLogFormCardioHtmxEndpoint() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "log-cardio@test.com")
        val response = client.get("/log/form?activityType=Running") {
            header("Cookie", cookie)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Distance")
    }

    @Test
    fun testLogFormWeightliftingHtmxEndpoint() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "log-lift@test.com")
        val response = client.get("/log/form?activityType=Weightlifting") {
            header("Cookie", cookie)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testLogFormWithBlankTypeShowsError() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "log-blank@test.com")
        val response = client.get("/log/form?activityType=") {
            header("Cookie", cookie)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "select an activity type")
    }

    // ==================== Workflow Tests ====================

    @Test
    fun testLogCardioWorkoutSuccess() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "log-cardio-wf@test.com")
        val response = client.submitForm("/log/submit", formParameters = parameters {
            append("type", "Running")
            append("activityDate", "2026-05-07")
            append("distance", "10")
            append("duration", "45")
            append("notes", "Test run")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "session logged")
    }

    @Test
    fun testLogWeightliftingWorkoutSuccess() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "log-lift-wf@test.com")
        val response = client.submitForm("/log/submit", formParameters = parameters {
            append("type", "Weightlifting")
            append("activityDate", "2026-05-07")
            append("duration", "60")
            append("exerciseName", "Bench Press")
            append("sets", "3")
            append("reps", "10")
            append("weight", "80")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "session saved")
    }

    @Test
    fun testLogCardioWorkoutWithMissingDurationShowsError() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "log-cardio-err@test.com")
        val response = client.submitForm("/log/submit", formParameters = parameters {
            append("type", "Running")
            append("activityDate", "2026-05-07")
            append("distance", "10")
            append("duration", "")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "valid date and duration")
    }

    @Test
    fun testProfileSaveUpdatesProfile() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "profile-save-wf@test.com")
        val response = client.submitForm("/profile/save", formParameters = parameters {
            append("name", "Updated Name")
            append("email", "profile-save-wf@test.com")
            append("fitnessLevel", "Advanced")
            append("goal", "Run a marathon")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Updated Name")
        assertContains(response.bodyAsText(), "Advanced")
    }

    @Test
    fun testProfileSaveWithBlankNameShowsError() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "profile-save-err@test.com")
        val response = client.submitForm("/profile/save", formParameters = parameters {
            append("name", "")
            append("email", "profile-save-err@test.com")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Name and email are required")
    }

    @Test
    fun testRaceLogSubmitCreatesRecord() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "race-submit@test.com")
        val response = client.submitForm("/races/log", formParameters = parameters {
            append("eventName", "Leeds 10K")
            append("eventDate", "2026-05-11")
            append("location", "Leeds")
            append("category", "Senior Men")
            append("finishTime", "42:30")
            append("overallRank", "150")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/activities?filter=races", response.headers[HttpHeaders.Location])
    }

    @Test
    fun testRaceLogWithInvalidCertificateUrlShowsError() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "race-cert-err@test.com")
        val response = client.submitForm("/races/log", formParameters = parameters {
            append("eventName", "Test Race")
            append("eventDate", "2026-05-11")
            append("certificateUrl", "ftp://bad.com/cert.pdf")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Certificate URL must start with")
    }

    @Test
    fun testRaceLogWithMissingNameAndDateShowsError() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "race-blank@test.com")
        val response = client.submitForm("/races/log", formParameters = parameters {
            append("eventName", "")
            append("eventDate", "")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Race name and date are required")
    }

    @Test
    fun testOnboardingWithValidPlanRedirectsToHome() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "onboard-submit@test.com")
        val response = client.submitForm("/onboarding", formParameters = parameters {
            append("planType", "beginner")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/home", response.headers[HttpHeaders.Location])
    }

    @Test
    fun testOnboardingWithBlankPlanShowsError() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "onboard-blank@test.com")
        val response = client.submitForm("/onboarding", formParameters = parameters {
            append("planType", "")
        }) { header("Cookie", cookie) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "choose a training plan")
    }

    // ==================== Plan Update Session ====================

    @Test
    fun testPlanUpdateSessionWithMissingDayRedirectsToHome() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "plan-update@test.com")
        val response = client.get("/plan/update-session") {
            header("Cookie", cookie)
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/home", response.headers[HttpHeaders.Location])
    }

    @Test
    fun testPlanUpdateSessionWithInvalidDayRedirectsToHome() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }
        val cookie = registerAndLogin(client, "plan-inv@test.com")
        val response = client.get("/plan/update-session?day=Notaday") {
            header("Cookie", cookie)
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/home", response.headers[HttpHeaders.Location])
    }
}