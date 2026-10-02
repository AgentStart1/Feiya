package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.PortEndpoint
import com.storyteller_f.feiya.service.PortHandoff
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PortHandoffTest {
    private class Endpoint(override val port: Int) : PortEndpoint {
        var target: Int? = null
        var closed = false
        override fun redirectTo(port: Int?) { target = port }
        override suspend fun close() { closed = true }
    }

    @Test fun everyOldPortTargetsLatestAndSwitchingBackReusesListener() = runTest {
        val bound = mutableListOf<Endpoint>()
        val handoff = PortHandoff { port -> Endpoint(port).also { bound.add(it) } }
        val first = handoff.start(8080)
        val second = handoff.start(8081)
        val third = handoff.start(8082)
        assertEquals(8082, first.target)
        assertEquals(8082, second.target)
        assertNull(third.target)
        assertSame(first, handoff.start(8080))
        assertNull(first.target)
        assertEquals(8080, second.target)
        assertEquals(8080, third.target)
        assertEquals(3, bound.size)
        handoff.stop()
        assertTrue(bound.all { it.closed })
        assertNull(handoff.current)
    }

    @Test fun failedBindKeepsCurrentAndExistingRedirectsWorking() = runTest {
        val handoff = PortHandoff { port ->
            check(port != 8082) { "port in use" }
            Endpoint(port)
        }
        val first = handoff.start(8080)
        val second = handoff.start(8081)
        val failure = runCatching { handoff.start(8082) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertSame(second, handoff.current)
        assertEquals(8081, first.target)
        assertNull(second.target)
        handoff.stop()
    }

    @Test fun samePortDoesNotRebindAndInvalidPortDoesNotStopServer() = runTest {
        var binds = 0
        val handoff = PortHandoff { port -> binds++; Endpoint(port) }
        val first = handoff.start(8080)
        assertSame(first, handoff.start(8080))
        assertTrue(runCatching { handoff.start(65536) }.exceptionOrNull() is IllegalArgumentException)
        assertSame(first, handoff.current)
        assertEquals(1, binds)
        handoff.stop()
        val restarted = handoff.start(8080)
        assertNotSame(first, restarted)
        handoff.stop()
    }

    @Test fun stopAttemptsEveryEndpointEvenIfOneFails() = runTest {
        val closed = mutableListOf<Int>()
        val handoff = PortHandoff { port -> object : PortEndpoint {
            override val port = port
            override fun redirectTo(port: Int?) = Unit
            override suspend fun close() { closed.add(port); check(port != 8080) }
        } }
        handoff.start(8080)
        handoff.start(8081)
        assertTrue(runCatching { handoff.stop() }.isFailure)
        assertEquals(setOf(8080, 8081), closed.toSet())
        assertNull(handoff.current)
    }
}
