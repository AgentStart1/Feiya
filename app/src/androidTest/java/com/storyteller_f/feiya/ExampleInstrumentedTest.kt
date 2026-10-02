package com.storyteller_f.feiya

import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.storyteller_f.feiya.service.AppServer
import com.storyteller_f.feiya.service.AppService
import com.storyteller_f.feiya.service.ServerState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import android.content.Context
import com.storyteller_f.feiya.service.SharedFileInfo
import kotlinx.coroutines.withTimeout

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val fixtureDirectory by lazy { File(context.cacheDir, "share-test-${UUID.randomUUID()}") }
    private val fixture by lazy { File(fixtureDirectory, "test.zip") }
    private val fixtureUri get() = fixture.toUri()

    @Before fun createFixture() {
        fixtureDirectory.mkdirs()
        fixture.writeText("test fixture")
    }

    @After fun removeFixture() {
        try {
            runBlocking {
                context.removeUri(SharedFileInfo(fixtureUri.toString(), fixture.name))
                context.cacheInvalid()
            }
        } finally {
            fixtureDirectory.deleteRecursively()
        }
    }

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun testServerStart() {
        useService { _, _, server ->
            runBlocking {
                server.onReceiveEventPort(AppService.DEFAULT_PORT, null)
            }
            assertTrue(server.state.value is ServerState.Started)
        }
    }

    @Test
    fun testServerStop() {
        useService { _, _, server ->
            runBlocking {
                server.onReceiveEventPort(AppService.DEFAULT_PORT, null)
                assertTrue(server.state.value is ServerState.Started)
                server.onReceiveEventPort(0, AppService.EVENT_STOP)
                assertTrue(server.state.value is ServerState.Stopped)
            }
        }
    }

    @Test
    fun testServerRestart() {
        useService { _, _, server ->
            runBlocking {
                server.onReceiveEventPort(AppService.DEFAULT_PORT, null)
                val preStartedTime = (server.state.value as ServerState.Started).time
                server.onReceiveEventPort(AppService.DEFAULT_PORT, AppService.EVENT_RESTART)
                assertNotEquals(preStartedTime, (server.state.value as ServerState.Started).time)
            }
        }
    }

    @Test
    fun testConflictPort() {
        useService { _, _, server ->
            otherServer {
                runBlocking {
                    server.onReceiveEventPort(AppService.DEFAULT_PORT, null)
                }
                assertTrue(server.state.value is ServerState.Error)
            }

        }
    }

    @Test
    fun testLogin() {
        useClient { serviceBinder, httpClient, _, _ ->
            serviceBinder.appendUri(fixtureUri)
            withTimeout(10_000) { shares.first { files -> files.any { it.uri == fixtureUri.toString() } } }
            val response =
                httpClient.get("http://${AppService.LISTENER_ADDRESS}:${AppService.DEFAULT_PORT}/shares")
            assertTrue(response.status.isSuccess())
            val bodyAsText = response.bodyAsText()
            assertTrue(bodyAsText.contains("test.zip"))
        }
    }

    @Test
    fun testSSE() {
        useSSE { serviceBinder, sseClient ->
            val deferred = async {
                val session = sseClient.sseSession(urlString = "http://${AppService.LISTENER_ADDRESS}:${AppService.DEFAULT_PORT}/sse")
                try {
                    withTimeout(10_000) { session.incoming.first { it.data == "refresh" } }
                } finally {
                    session.cancel()
                }
            }
            launch {
                delay(1000)
                serviceBinder.appendUri(fixtureUri)
            }
            deferred.await()
        }
    }

    private fun useSSE(block: suspend CoroutineScope.(AppService.ServiceBinder, sseClient: HttpClient) -> Unit) {
        useClient { serviceBinder, _, _, cookie ->
            HttpClient {
                install(SSE) {
                    showCommentEvents()
                    showRetryEvents()
                }
                install(Logging)
                defaultRequest {
                    headers {
                        append("cookie", cookie)
                    }
                }
            }.use {
                block(serviceBinder, it)
            }
        }
    }

    private fun useService(block: (AppService.ServiceBinder, AppService, AppServer) -> Unit) {
        val serviceIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            AppService::class.java
        )
        try {
            val binder = serviceRule.bindService(serviceIntent)
            val serviceBinder = binder as AppService.ServiceBinder
            val service = serviceBinder.service
            service.stop()
            runBlocking {
                service.server.state.first { it is ServerState.Stopped }
            }
            block(serviceBinder, service, service.server)
        } finally {
            serviceRule.unbindService()
        }
    }

    private fun useClient(block: suspend CoroutineScope.(AppService.ServiceBinder, HttpClient, AppService, cookie: String) -> Unit) {
        useService { serviceBinder, service, server ->
            uriFilePath = File(service.filesDir, "list.txt").absolutePath
            val cookieMap = mutableMapOf<String, String>()
            HttpClient(CIO) {
                install(Logging)
                defaultRequest {
                    headers {
                        cookieMap.forEach(::append)
                    }
                }
            }.use {
                runBlocking {
                    server.onReceiveEventPort(AppService.DEFAULT_PORT, null)
                    val cookie = it.submitForm(
                        "http://${AppService.LISTENER_ADDRESS}:${AppService.DEFAULT_PORT}/login",
                        formParameters = parameters {
                            append("user", "hidden")
                            append("password", "")
                        }).headers["set-cookie"]!!
                    cookieMap["cookie"] = cookie
                    block(serviceBinder, it, service, cookie)
                }
            }
        }
    }

    private fun otherServer(block: () -> Unit) {
        val engine = embeddedServer(
            Netty,
            port = AppService.DEFAULT_PORT,
            host = AppService.LISTENER_ADDRESS
        ) {
        }.start(wait = false)
        try {
            block()
        } finally {
            engine.stop()
        }
    }

}