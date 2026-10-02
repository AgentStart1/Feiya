package com.storyteller_f.feiya

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HidSendTaskTest {
    private class Connection(override val deviceName: String = "computer") : HidKeyboardConnection {
        override var isConnected = true
        val keys = mutableListOf<HidKey>()
        override suspend fun sendRawKey(key: HidKey): HidKeyResult {
            keys.add(key)
            delay(100)
            return HidKeyResult.SENT
        }
    }

    @Test fun actionBindsConnectionBeforeDispatcherRuns() = runTest {
        val old = Connection()
        val replacement = Connection()
        val connections = MutableStateFlow<HidKeyboardConnection?>(old)
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler), connections)
        host.sendText("ab")
        connections.value = replacement
        host.sendText("c")
        advanceUntilIdle()
        assertTrue(old.keys.isEmpty())
        assertEquals(listOf(HidKey(6)), replacement.keys)
        assertEquals(HidTaskFailure.CONNECTION_CHANGED, host.state.value.tasks.first().failure)
        assertEquals(HidTaskStatus.SENT, host.state.value.tasks.last().status)
        host.close()
    }

    @Test fun reconnectSameDeviceAbortsActiveAndQueuedMessages() = runTest {
        val old = Connection()
        val replacement = Connection() // Same name/device, different connection lifetime.
        val connections = MutableStateFlow<HidKeyboardConnection?>(old)
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler), connections)
        host.sendText("abc")
        host.sendText("d")
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, host.state.value.tasks.first().sentKeys)
        old.isConnected = false
        connections.value = replacement
        host.sendText("e")
        advanceUntilIdle()
        assertEquals(listOf(HidKey(4), HidKey(5)), old.keys)
        assertEquals(listOf(HidKey(8)), replacement.keys)
        assertEquals(listOf(HidTaskFailure.CONNECTION_CHANGED, HidTaskFailure.CONNECTION_CHANGED, null), host.state.value.tasks.map { it.failure })
        host.close()
    }

    @Test fun cancellationBeforeWorkerStartsDoesNotReviveTask() = runTest {
        val connection = Connection()
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler), MutableStateFlow(connection))
        host.sendText("ab")
        host.cancelTask(1)
        advanceUntilIdle()
        assertTrue(connection.keys.isEmpty())
        assertEquals(HidTaskStatus.CANCELLED, host.state.value.tasks.single().status)
        host.close()
    }

    @Test fun progressQueueAndIndividualCancellation() = runTest {
        val connection = Connection()
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler), MutableStateFlow(connection))
        host.sendText("abc")
        host.sendText("d")
        runCurrent()
        assertEquals(listOf(HidTaskStatus.SENDING, HidTaskStatus.QUEUED), host.state.value.tasks.map { it.status })
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, host.state.value.tasks.first().sentKeys)
        host.cancelTask(2)
        host.cancelTask(1)
        advanceUntilIdle()
        assertEquals(listOf(HidTaskStatus.CANCELLED, HidTaskStatus.CANCELLED), host.state.value.tasks.map { it.status })
        assertEquals(listOf(HidKey(4), HidKey(5)), connection.keys)
        host.sendText("e")
        advanceUntilIdle()
        assertEquals(HidTaskStatus.SENT, host.state.value.tasks.last().status)
        assertEquals(1, host.state.value.tasks.last().sentKeys)
        host.close()
    }

    @Test fun cancelAllAndCloseTerminalizePendingWork() = runTest {
        val connection = Connection()
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler), MutableStateFlow(connection))
        repeat(3) { host.sendText("abc") }
        runCurrent()
        host.cancelAll()
        advanceUntilIdle()
        assertTrue(host.state.value.tasks.all { it.status == HidTaskStatus.CANCELLED })
        assertEquals(1, connection.keys.size)
        repeat(2) { host.sendText("d") }
        runCurrent()
        host.close()
        advanceUntilIdle()
        assertTrue(host.state.value.tasks.all { it.status == HidTaskStatus.CANCELLED })
    }

    @Test fun disconnectedSubmissionIsNotRetargetedAndHistoryIsBounded() = runTest {
        val connections = MutableStateFlow<HidKeyboardConnection?>(null)
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler), connections)
        host.sendText("a")
        connections.value = Connection()
        runCurrent()
        assertEquals(HidTaskFailure.NOT_CONNECTED, host.state.value.tasks.single().failure)
        repeat(15) { host.sendText("b") }
        advanceUntilIdle()
        assertEquals(10, host.state.value.tasks.size)
        assertTrue(host.state.value.tasks.all { it.status == HidTaskStatus.SENT })
        host.close()
    }
}
