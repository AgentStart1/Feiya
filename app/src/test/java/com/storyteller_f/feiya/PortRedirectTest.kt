package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.installPortRedirect
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PortRedirectTest {
    @Test fun oldLinksPreserveHostPathAndQueryAndDoNotCacheRedirect() = testApplication {
        val target = MutableStateFlow<Int?>(8081)
        application { installPortRedirect(target) }
        val browser = createClient { followRedirects = false }
        val response = browser.get("/shares/2?name=a%20b") { header(HttpHeaders.Host, "192.168.1.2:8080") }
        assertEquals(HttpStatusCode.TemporaryRedirect, response.status)
        assertEquals("http://192.168.1.2:8081/shares/2?name=a%20b", response.headers[HttpHeaders.Location])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        target.value = 8082
        assertEquals("http://192.168.1.2:8082/login", browser.post("/login") { header(HttpHeaders.Host, "192.168.1.2:8080") }.headers[HttpHeaders.Location])
    }

    @Test fun ipv6AndReturningToAnOldPortWork() = testApplication {
        val target = MutableStateFlow<Int?>(8081)
        application {
            installPortRedirect(target)
            routing { get("/") { call.respondText("active") } }
        }
        val browser = createClient { followRedirects = false }
        assertEquals("http://[::1]:8081/", browser.get("/") { header(HttpHeaders.Host, "[::1]:8080") }.headers[HttpHeaders.Location])
        target.value = null
        assertEquals(HttpStatusCode.OK, browser.get("/") { header(HttpHeaders.Host, "[::1]:8080") }.status)
    }
}
