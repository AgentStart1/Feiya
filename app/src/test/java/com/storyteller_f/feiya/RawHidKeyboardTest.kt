package com.storyteller_f.feiya

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RawHidKeyboardTest {
    @Test fun shortcutUsesDescriptorReportFormatAndReleasesModifiers() = runTest {
        val reports = mutableListOf<List<Byte>>()
        val sender = RawHidKeyboard()
        assertTrue(sender.sendRawKey(HidKey(0x06, 0x09)) { reports.add(it.toList()); true })
        assertEquals(listOf(listOf<Byte>(9, 6), listOf<Byte>(0, 0)), reports)
    }

    @Test fun failedPressStillReleasesAndReturnsFailure() = runTest {
        val reports = mutableListOf<List<Byte>>()
        assertFalse(RawHidKeyboard().sendRawKey(HidKey(4)) {
            reports.add(it.toList())
            it[1] == 0.toByte()
        })
        assertEquals(listOf(listOf<Byte>(0, 4), listOf<Byte>(0, 0)), reports)
    }

    @Test fun failedReleaseReturnsFailure() = runTest {
        assertFalse(RawHidKeyboard().sendRawKey(HidKey(4)) { it[1] != 0.toByte() })
    }

    @Test fun transportExceptionStillAttemptsReleaseAndUnlocksSender() = runTest {
        val reports = mutableListOf<List<Byte>>()
        val sender = RawHidKeyboard()
        try {
            sender.sendRawKey(HidKey(4)) {
                reports.add(it.toList())
                if (it[1] != 0.toByte()) throw SecurityException("permission revoked")
                true
            }
            fail("Expected the transport failure")
        } catch (_: SecurityException) {
            assertEquals(listOf(listOf<Byte>(0, 4), listOf<Byte>(0, 0)), reports)
        }
        assertTrue(sender.sendRawKey(HidKey(5)) { true })
    }

    @Test fun cancellationReleasesKeyAndUnlocksSender() = runTest {
        val reports = mutableListOf<List<Byte>>()
        val sender = RawHidKeyboard()
        val task = launch { sender.sendRawKey(HidKey(4, 2)) { reports.add(it.toList()); true } }
        runCurrent()
        task.cancelAndJoin()
        assertEquals(listOf(listOf<Byte>(2, 4), listOf<Byte>(0, 0)), reports)
        assertTrue(sender.sendRawKey(HidKey(5)) { true })
    }

    @Test fun concurrentKeysCannotInterleavePressAndRelease() = runTest {
        val reports = mutableListOf<List<Byte>>()
        val sender = RawHidKeyboard()
        val tasks = (4..5).map { usage ->
            launch { sender.sendRawKey(HidKey(usage)) { reports.add(it.toList()); true } }
        }
        tasks.forEach { it.join() }
        assertEquals(listOf(listOf<Byte>(0, 4), listOf<Byte>(0, 0), listOf<Byte>(0, 5), listOf<Byte>(0, 0)), reports)
    }
}
