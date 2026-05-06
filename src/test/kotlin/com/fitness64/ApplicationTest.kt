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
     * Verifies that /calendar redirects unauthenticated users to /login.
     */
    @Test
    fun testCalendarRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/calendar")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /plan redirects unauthenticated users to /login.
     */
    @Test
    fun testPlanRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/plan")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    /**
     * Verifies that /races redirects unauthenticated users to /login.
     */
    @Test
    fun testRacesRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/races")
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
     * Verifies that posting to the TCX upload endpoint redirects unauthenticated users.
     */
    @Test
    fun testTcxUploadPostRedirectsWhenNotLoggedIn() = testApplication {
        application { module() }
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.post("/tcx/upload")
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
}