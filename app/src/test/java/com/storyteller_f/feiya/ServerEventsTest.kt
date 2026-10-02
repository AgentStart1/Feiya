package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.AppService
import com.storyteller_f.feiya.service.PortEndpoint
import com.storyteller_f.feiya.service.PortHandoff
import com.storyteller_f.feiya.service.collectServerEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerEventsTest {
    @Test fun stopThenChangePortThenStopClosesTheNewListener() = runTest {
        val ports = MutableStateFlow(8080)
        val commands = Channel<Int>(Channel.UNLIMITED)
        val closed = mutableListOf<Int>()
        val handoff = PortHandoff { port ->
            object : PortEndpoint {
                override val port = port
                override fun redirectTo(port: Int?) = Unit
                override suspend fun close() { closed.add(port) }
            }
        }
        backgroundScope.launch {
            collectServerEvents(ports, commands) { port, command ->
                if (command == AppService.EVENT_STOP) handoff.stop() else handoff.start(port)
            }
        }
        runCurrent()
        commands.send(AppService.EVENT_STOP)
        runCurrent()
        assertNull(handoff.current)
        ports.value = 9090
        runCurrent()
        assertEquals(9090, handoff.current!!.port)
        commands.send(AppService.EVENT_STOP)
        runCurrent()
        assertNull(handoff.current)
        assertEquals(listOf(8080, 9090), closed)
    }

    @Test fun identicalCommandsAreNotConflatedOrReplayedOnPortChange() = runTest {
        val ports = MutableStateFlow(8080)
        val commands = Channel<Int>(Channel.UNLIMITED)
        val received = mutableListOf<Pair<Int, Int?>>()
        backgroundScope.launch {
            collectServerEvents(ports, commands) { port, command -> received.add(port to command) }
        }
        runCurrent()
        commands.send(AppService.EVENT_RESTART)
        commands.send(AppService.EVENT_RESTART)
        runCurrent()
        ports.value = 9090
        runCurrent()
        assertEquals(listOf(8080 to null, 8080 to AppService.EVENT_RESTART,
            8080 to AppService.EVENT_RESTART, 9090 to null), received)
    }

    @Test fun startupCommandIsConsumedAndCancellationLeavesNoConsumer() = runTest {
        val commands = Channel<Int>(Channel.UNLIMITED)
        commands.send(AppService.EVENT_OFF)
        val ports = MutableStateFlow(8080)
        val received = mutableListOf<Int?>()
        val collector = backgroundScope.launch {
            collectServerEvents(ports, commands) { _, command -> received.add(command) }
        }
        runCurrent()
        assertEquals(listOf(AppService.EVENT_OFF), received)
        assertTrue(commands.tryReceive().isFailure)
        ports.value = 9090
        runCurrent()
        assertEquals(listOf(AppService.EVENT_OFF, null), received)
        collector.cancel()
        runCurrent()
        commands.send(AppService.EVENT_STOP)
        runCurrent()
        assertEquals(AppService.EVENT_STOP, commands.tryReceive().getOrThrow())
    }
}
