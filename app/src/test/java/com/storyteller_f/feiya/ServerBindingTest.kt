package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.BoundServer
import com.storyteller_f.feiya.service.ServerBinding
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ServerBindingTest {
    private class Endpoint(override val port: Int) : BoundServer {
        var closed = false
        override suspend fun close() { closed = true }
    }

    @Test fun switchClosesOldListenerAndSwitchingBackBindsAgain() = runTest {
        val bound = mutableListOf<Endpoint>()
        val binding = ServerBinding { port -> Endpoint(port).also { bound.add(it) } }
        val first = binding.start(8080)
        val second = binding.start(8081)
        val third = binding.start(8082)
        assertTrue(first.closed)
        assertTrue(second.closed)
        assertFalse(third.closed)
        assertNotSame(first, binding.start(8080))
        assertTrue(third.closed)
        assertEquals(4, bound.size)
        binding.stop()
        assertTrue(bound.all { it.closed })
        assertNull(binding.current)
    }

    @Test fun failedBindKeepsCurrentWorking() = runTest {
        val binding = ServerBinding { port ->
            check(port != 8082) { "port in use" }
            Endpoint(port)
        }
        val first = binding.start(8080)
        val second = binding.start(8081)
        val failure = runCatching { binding.start(8082) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertSame(second, binding.current)
        assertTrue(first.closed)
        assertFalse(second.closed)
        binding.stop()
    }

    @Test fun samePortDoesNotRebindAndInvalidPortDoesNotStopServer() = runTest {
        var binds = 0
        val binding = ServerBinding { port -> binds++; Endpoint(port) }
        val first = binding.start(8080)
        assertSame(first, binding.start(8080))
        assertTrue(runCatching { binding.start(65536) }.exceptionOrNull() is IllegalArgumentException)
        assertSame(first, binding.current)
        assertEquals(1, binds)
        binding.stop()
        val restarted = binding.start(8080)
        assertNotSame(first, restarted)
        binding.stop()
    }

    @Test fun stopClearsCurrentEvenIfCloseFails() = runTest {
        val closed = mutableListOf<Int>()
        val binding = ServerBinding { port -> object : BoundServer {
            override val port = port
            override suspend fun close() { closed.add(port); check(port != 8080) }
        } }
        binding.start(8080)
        assertTrue(runCatching { binding.stop() }.isFailure)
        assertEquals(listOf(8080), closed)
        assertNull(binding.current)
    }
}
