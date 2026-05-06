package com.fitness64

import com.fitness64.core.module
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @BeforeTest
    fun setup() {
        // Set test mode before initializing the application
        // This triggers the use of in-memory H2 database instead of SQLite
        System.setProperty("test.database", "true")
    }

    @Test
    fun testApplicationStarts() = testApplication {
        application {
            module()
        }
        // Verify the application started without errors
        // If we get here without exception, the test passes
    }

    @Test
    fun testLoginPageLoads() = testApplication {
        application {
            module()
        }
        val response = client.get("/login")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testRegisterPageLoads() = testApplication {
        application {
            module()
        }
        val response = client.get("/register")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
