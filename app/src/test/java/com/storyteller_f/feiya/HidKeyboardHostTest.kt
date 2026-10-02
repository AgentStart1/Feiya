package com.storyteller_f.feiya

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HidKeyboardHostTest {
    @Test fun unsupportedTextSendsNothingAndLaterTextStillWorks() = runTest {
        val sent = mutableListOf<HidKey>()
        val effects = mutableListOf<HidKeyboardEffect>()
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler)) { sent.add(it); true }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { host.effects.collect { effects.add(it) } }
        host.sendText("valid prefix then 😀")
        host.sendText("A")
        advanceUntilIdle()
        assertEquals(listOf(HidKey(4, 2)), sent)
        assertEquals(listOf(HidKeyboardEffect.UNSUPPORTED_TEXT), effects)
        host.close()
    }

    @Test fun transportFailureStopsCurrentTextButKeepsConsumerAlive() = runTest {
        val sent = mutableListOf<HidKey>()
        val effects = mutableListOf<HidKeyboardEffect>()
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler)) {
            sent.add(it)
            if (it.usage == 4) throw SecurityException("permission revoked")
            it.usage != 5
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { host.effects.collect { effects.add(it) } }
        host.sendText("abc")
        host.sendText("bc")
        host.sendText("d")
        advanceUntilIdle()
        assertEquals(listOf(HidKey(4), HidKey(5), HidKey(7)), sent)
        assertEquals(listOf(HidKeyboardEffect.SEND_FAILED, HidKeyboardEffect.SEND_FAILED), effects)
        host.close()
    }

    @Test fun calibrationAndShortcutsBypassEveryTextLayout() = runTest {
        for (layout in TargetKeyboardLayout.entries) {
            val sent = mutableListOf<HidKey>()
            val host = HidKeyboardHost(StandardTestDispatcher(testScheduler)) { sent.add(it); true }
            host.selectLayout(layout)
            host.sendLeftCalibrationKey()
            host.sendRightCalibrationKey()
            host.selectCalibration(KeyboardCalibration.ISO)
            host.sendLeftCalibrationKey()
            host.sendRightCalibrationKey()
            host.sendRawKey(HidKey(0x06, 0x01))
            advanceUntilIdle()
            assertEquals(listOf(HidKey(0x1d), HidKey(0x38), HidKey(0x64), HidKey(0x38), HidKey(6, 1)), sent)
            assertEquals(layout, host.state.value.layout)
            assertEquals(KeyboardCalibration.ISO, host.state.value.calibration)
            host.close()
        }
    }

    @Test fun queuedTextKeepsItsLayoutAndMessagesDoNotInterleave() = runTest {
        val sent = mutableListOf<HidKey>()
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler)) {
            delay(100)
            sent.add(it)
            true
        }
        host.selectLayout(TargetKeyboardLayout.DVORAK)
        host.editContent("ee")
        host.sendContent()
        host.selectLayout(TargetKeyboardLayout.COLEMAK)
        host.sendText("e")
        assertEquals(HidKeyboardState(), host.state.value) // Mutations use the injected dispatcher.
        advanceUntilIdle()
        assertEquals(listOf(HidKey(7), HidKey(7), HidKey(0x0e)), sent)
        assertEquals("ee", host.state.value.content)
        host.close()
    }

    @Test fun closingHostCancelsActiveSendAndDiscardsPendingWork() = runTest {
        val sent = mutableListOf<HidKey>()
        val cancelled = mutableListOf<HidKey>()
        val host = HidKeyboardHost(StandardTestDispatcher(testScheduler)) {
            sent.add(it)
            try { delay(1000) } catch (error: CancellationException) { cancelled.add(it); throw error }
            true
        }
        host.sendText("ab")
        host.sendText("c")
        runCurrent()
        host.close()
        advanceUntilIdle()
        assertEquals(listOf(HidKey(4)), sent)
        assertEquals(sent, cancelled)
        host.sendText("d")
        advanceUntilIdle()
        assertEquals(1, sent.size)
    }
}
