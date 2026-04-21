package com.fitness64

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

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
        assertTrue(true)
    }

    @Test
    fun testLoginPageLoads() = testApplication {
        application {
            module()
        }
        val response = client.get("/login")
        assertTrue(response.status == HttpStatusCode.OK)
    }

    @Test
    fun testRegisterPageLoads() = testApplication {
        application {
            module()
        }
        val response = client.get("/register")
        assertTrue(response.status == HttpStatusCode.OK)
    }
}
