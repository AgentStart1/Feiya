package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.BoundServer
import com.storyteller_f.feiya.service.ServerBinding
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class ServerBindingIntegrationTest {
    @Test fun successfulSwitchReleasesOldPortAndFailedSwitchKeepsCurrent() = runTest {
        val addresses = List(3) { ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")) }
        val ports = addresses.map { it.localPort }
        addresses.forEach { it.close() }
        val binding = ServerBinding { port ->
            val server = embeddedServer(Netty, host = "127.0.0.1", port = port) {
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
            object : BoundServer {
                override val port = port
                override suspend fun close() { withContext(Dispatchers.IO) { server.stop(0, 1000) } }
            }
        }
        val client = HttpClient(CIO) { followRedirects = false }
        try {
            binding.start(ports[0])
            binding.start(ports[1])
            val download = client.get("http://127.0.0.1:${ports[1]}/download")
            assertNotEquals("0", download.headers[HttpHeaders.ContentLength])
            assertEquals("download-body", download.bodyAsText())
            ServerSocket(ports[0], 0, InetAddress.getByName("127.0.0.1")).use { assertTrue(it.isBound) }
            ServerSocket(ports[2], 0, InetAddress.getByName("127.0.0.1")).use {
                assertTrue(runCatching { binding.start(ports[2]) }.isFailure)
                assertEquals("port=${ports[1]}", client.get("http://127.0.0.1:${ports[1]}/check").bodyAsText())
            }
            binding.start(ports[0])
            assertEquals("port=${ports[0]}", client.get("http://127.0.0.1:${ports[0]}/check").bodyAsText())
            ServerSocket(ports[1], 0, InetAddress.getByName("127.0.0.1")).use { assertTrue(it.isBound) }
        } finally {
            client.close()
            binding.stop()
        }
        ports.take(2).forEach { port ->
            ServerSocket(port, 0, InetAddress.getByName("127.0.0.1")).use { assertTrue(it.isBound) }
        }
    }
}
