package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.AppServer
import com.storyteller_f.feiya.service.AppService
import com.storyteller_f.feiya.service.ServerState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FeiyaApplication::class)
class AppServerFailureTest {
    @Test fun failedSwitchPreservesMessagingCacheAndRefreshEvents() = runTest {
        // Attach a service context without onCreate's automatic listener startup.
        val service = Robolectric.buildService(AppService::class.java).get()
        val server = AppServer(service, Dispatchers.Default.limitedParallelism(1))
        val address = InetAddress.getByName("127.0.0.1")
        val available = ServerSocket(0, 0, address).use { it.localPort }
        try {
            withContext(Dispatchers.IO) {
                withTimeout(30_000) {
                    server.onReceiveEventPort(available, null)
                    val active = server.state.value as ServerState.Started
                    ServerSocket(0, 0, address).use { occupied ->
                        server.onReceiveEventPort(occupied.localPort, null)
                        assertSame(active, server.state.value)
                        val failure = server.portSwitchFailures.first()
                        assertEquals(occupied.localPort, failure.requestedPort)
                        assertEquals(available, failure.activePort)
                    }
                    assertNotNull(server.messagesCache)
                    server.sendMessage("after failed port switch")
                    val messages = server.messagesCache!!.first { list ->
                        list.any { it.data == "after failed port switch" }
                    }
                    assertEquals("after failed port switch", messages.last().data)
                    val refresh = async(start = CoroutineStart.UNDISPATCHED) { active.channel.first() }
                    server.emitRefreshEvent()
                    assertEquals("refresh", refresh.await().data)
                    // Invalid ports use the same fallback and separate error path.
                    server.onReceiveEventPort(0, null)
                    assertSame(active, server.state.value)
                    assertEquals(0, server.portSwitchFailures.first().requestedPort)
                }
            }
        } finally {
            server.stopBlocking()
        }
    }

    @Test fun failureWithoutAnyActiveEndpointStillPublishesError() = runTest {
        val service = Robolectric.buildService(AppService::class.java).get()
        val server = AppServer(service, Dispatchers.Default.limitedParallelism(1))
        try {
            server.onReceiveEventPort(0, null)
            assertTrue(server.state.value is ServerState.Error)
            assertNull(server.messagesCache)
        } finally {
            server.stopBlocking()
        }
    }
}
