package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.PortEndpoint
import com.storyteller_f.feiya.service.PortHandoff
import com.storyteller_f.feiya.service.installPortRedirect
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import java.io.ByteArrayInputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class PortHandoffIntegrationTest {
    @Test fun realListenersRedirectSurviveBindFailureAndReleasePorts() = runTest {
        val addresses = List(3) { ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")) }
        val ports = addresses.map { it.localPort }
        addresses.forEach { it.close() }
        val handoff = PortHandoff { port ->
            val target = MutableStateFlow<Int?>(null)
            val server = embeddedServer(Netty, host = "127.0.0.1", port = port) {
                installPortRedirect(target)
                routing {
                    get("/check") { call.respondText("port=$port") }
                    get("/download") {
                        call.respond(UriFileContent(ContentType.Text.Plain, null, null, CoroutineScope(currentCoroutineContext())) {
                            ByteArrayInputStream("download-body".toByteArray())
                        })
                    }
                }
            }
            try { withContext(Dispatchers.IO) { server.start(wait = false) } }
            catch (failure: Throwable) { withContext(Dispatchers.IO) { server.stop(0, 1000) }; throw failure }
            object : PortEndpoint {
                override val port = port
                override fun redirectTo(port: Int?) { target.value = port }
                override suspend fun close() { withContext(Dispatchers.IO) { server.stop(0, 1000) } }
            }
        }
        val client = HttpClient(CIO) { followRedirects = false }
        try {
            handoff.start(ports[0])
            handoff.start(ports[1])
            val download = client.get("http://127.0.0.1:${ports[1]}/download")
            assertNotEquals("0", download.headers[HttpHeaders.ContentLength])
            assertEquals("download-body", download.bodyAsText())
            val old = client.get("http://127.0.0.1:${ports[0]}/check?x=1")
            assertEquals(HttpStatusCode.TemporaryRedirect, old.status)
            assertEquals("http://127.0.0.1:${ports[1]}/check?x=1", old.headers[HttpHeaders.Location])
            ServerSocket(ports[2], 0, InetAddress.getByName("127.0.0.1")).use {
                assertTrue(runCatching { handoff.start(ports[2]) }.isFailure)
                assertEquals("port=${ports[1]}", client.get("http://127.0.0.1:${ports[1]}/check").bodyAsText())
            }
            handoff.start(ports[0])
            assertEquals("port=${ports[0]}", client.get("http://127.0.0.1:${ports[0]}/check").bodyAsText())
            assertEquals(HttpStatusCode.TemporaryRedirect, client.get("http://127.0.0.1:${ports[1]}/check").status)
        } finally {
            client.close()
            handoff.stop()
        }
        ports.take(2).forEach { port ->
            ServerSocket(port, 0, InetAddress.getByName("127.0.0.1")).use { assertTrue(it.isBound) }
        }
    }
}
