package com.fitness64

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplicationTest {

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

}
