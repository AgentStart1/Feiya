package com.storyteller_f.feiya

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HidKeyboardSessionTest {
    @Test fun disconnectReleasesOriginalTargetAndRejectsRemainingKeys() = runTest {
        val reports = mutableListOf<List<Byte>>()
        val session = HidKeyboardSession("A", StandardTestDispatcher(testScheduler), RawHidKeyboard()) {
            reports.add(it.toList()); true
        }
        val result = async { session.sendRawKey(HidKey(4)) }
        runCurrent()
        session.disconnect()
        advanceUntilIdle()
        assertEquals(HidKeyResult.CONNECTION_CHANGED, result.await())
        assertEquals(listOf(listOf<Byte>(0, 4), listOf<Byte>(0, 0)), reports)
        assertEquals(HidKeyResult.CONNECTION_CHANGED, session.sendRawKey(HidKey(5)))
        assertEquals(2, reports.size)
    }

    @Test fun invalidatedSessionWaitingForKeyboardNeverPresses() = runTest {
        val keyboard = RawHidKeyboard()
        val firstReports = mutableListOf<List<Byte>>()
        val secondReports = mutableListOf<List<Byte>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = HidKeyboardSession("A", dispatcher, keyboard) { firstReports.add(it.toList()); true }
        val second = HidKeyboardSession("B", dispatcher, keyboard) { secondReports.add(it.toList()); true }
        val firstJob = async { first.sendRawKey(HidKey(4)) }
        val secondJob = async { second.sendRawKey(HidKey(5)) }
        runCurrent()
        second.disconnect()
        advanceUntilIdle()
        assertEquals(HidKeyResult.SENT, firstJob.await())
        assertEquals(HidKeyResult.CONNECTION_CHANGED, secondJob.await())
        assertTrue(secondReports.isEmpty())
        assertEquals(2, firstReports.size)
    }

    @Test fun cancellingTaskReleasesKeyBeforeNextTask() = runTest {
        val reports = mutableListOf<List<Byte>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = HidKeyboardSession("A", dispatcher, RawHidKeyboard()) { reports.add(it.toList()); true }
        val host = HidKeyboardHost(dispatcher, kotlinx.coroutines.flow.MutableStateFlow(connection))
        host.sendText("ab")
        host.sendText("c")
        runCurrent()
        host.cancelTask(1)
        advanceUntilIdle()
        assertEquals(listOf(listOf<Byte>(0, 4), listOf<Byte>(0, 0), listOf<Byte>(0, 6), listOf<Byte>(0, 0)), reports)
        assertEquals(listOf(HidTaskStatus.CANCELLED, HidTaskStatus.SENT), host.state.value.tasks.map { it.status })
        host.close()
    }
}
