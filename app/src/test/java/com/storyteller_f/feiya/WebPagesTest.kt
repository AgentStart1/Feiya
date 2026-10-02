package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.AppServer
import com.storyteller_f.feiya.service.AppService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FeiyaApplication::class)
class WebPagesTest {
    @Test fun loginAssetsAndAuthenticatedPagesAreServedByTheRealRouter() = runTest {
        val service = Robolectric.buildService(AppService::class.java).get()
        val server = AppServer(service, Dispatchers.Default.limitedParallelism(1))
        val port = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        try {
            withContext(Dispatchers.IO) {
                server.onReceiveEventPort(port, null)
                fun request(path: String, cookie: String? = null, form: String? = null): Triple<Int, String, String?> {
                    val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    cookie?.let { connection.setRequestProperty("Cookie", it) }
                    try {
                        if (form != null) {
                            connection.requestMethod = "POST"
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                            connection.outputStream.use { it.write(form.toByteArray()) }
                        }
                        val status = connection.responseCode
                        val body = (if (status >= 400) connection.errorStream else connection.inputStream)
                            ?.bufferedReader()?.use { it.readText() }.orEmpty()
                        return Triple(status, body, connection.getHeaderField("Set-Cookie"))
                    } finally { connection.disconnect() }
                }
                assertEquals(302, request("/").first)
                assertEquals(302, request("/messages").first)
                val login = request("/login")
                assertEquals(200, login.first)
                assertTrue(login.second.contains("type=\"password\""))
                for (path in listOf("style.css", "app.js", "worker.js", "host.js", "icons/file.svg")) {
                    val asset = request("/web/$path")
                    assertEquals(path, 200, asset.first)
                    assertFalse(path, asset.second.contains("<!doctype html>"))
                }
                val signedIn = request("/login", form = "user=hidden&password=")
                assertEquals(302, signedIn.first)
                val cookie = requireNotNull(signedIn.third).substringBefore(';')
                val files = request("/", cookie)
                assertEquals(files.second, 200, files.first)
                assertTrue(files.second.take(500), files.second.contains("data-page=\"files\""))
                val chat = request("/messages", cookie)
                assertEquals(200, chat.first)
                assertTrue(chat.second.contains("data-page=\"chat\""))
            }
        } finally { server.stopBlocking() }
    }
}
