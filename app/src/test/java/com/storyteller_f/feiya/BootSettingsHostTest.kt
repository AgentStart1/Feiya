package com.storyteller_f.feiya

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BootSettingsHostTest {
    @Test fun loadsOffAndOnlyShowsEnabledAfterSaveCompletes() = runTest {
        val saved = CompletableDeferred<Unit>()
        val settings = object : BootSettings {
            var value = false
            override suspend fun read() = value
            override suspend fun save(enabled: Boolean) { saved.await(); value = enabled }
        }
        val host = BootSettingsHost(StandardTestDispatcher(testScheduler), settings)
        assertTrue(host.state.value.loading)
        runCurrent()
        assertFalse(host.state.value.enabled)
        host.setEnabled(true)
        runCurrent()
        assertTrue(host.state.value.saving)
        assertFalse(host.state.value.enabled)
        saved.complete(Unit)
        advanceUntilIdle()
        assertEquals(BootSettingsState(enabled = true, loading = false), host.state.value)
        host.close()
        val reopened = BootSettingsHost(StandardTestDispatcher(testScheduler), settings)
        advanceUntilIdle()
        assertTrue(reopened.state.value.enabled)
        reopened.close()
    }

    @Test fun repeatedReloadDoesNotOverlapPendingRead() = runTest {
        val result = CompletableDeferred<Boolean>()
        var reads = 0
        val host = BootSettingsHost(StandardTestDispatcher(testScheduler), object : BootSettings {
            override suspend fun read(): Boolean { reads++; return result.await() }
            override suspend fun save(enabled: Boolean) = Unit
        })
        runCurrent()
        host.reload()
        host.reload()
        runCurrent()
        assertEquals(1, reads)
        result.complete(true)
        runCurrent()
        assertEquals(BootSettingsState(enabled = true, loading = false), host.state.value)
        host.close()
    }

    @Test fun failedWriteKeepsPersistedValueAndAllowsRetry() = runTest {
        val settings = object : BootSettings {
            var fail = true
            override suspend fun read() = true
            override suspend fun save(enabled: Boolean) { if (fail) throw IOException("disk full") }
        }
        val host = BootSettingsHost(StandardTestDispatcher(testScheduler), settings)
        runCurrent()
        host.setEnabled(false)
        runCurrent()
        assertTrue(host.state.value.enabled)
        assertTrue(host.state.value.error)
        assertFalse(host.state.value.saving)
        settings.fail = false
        host.setEnabled(false)
        runCurrent()
        assertEquals(BootSettingsState(loading = false), host.state.value)
        host.close()
    }

    @Test fun failedReadDisablesEditsUntilSuccessfulRetry() = runTest {
        val settings = object : BootSettings {
            var fail = true
            override suspend fun read(): Boolean {
                if (fail) throw IOException("unavailable")
                return true
            }
            override suspend fun save(enabled: Boolean) { fail("Must not overwrite unknown setting") }
        }
        val host = BootSettingsHost(StandardTestDispatcher(testScheduler), settings)
        runCurrent()
        host.setEnabled(false)
        runCurrent()
        assertTrue(host.state.value.error)
        assertTrue(host.state.value.loading)
        settings.fail = false
        host.reload()
        runCurrent()
        assertEquals(BootSettingsState(enabled = true, loading = false), host.state.value)
        host.close()
    }

    @Test fun closingHostCancelsPendingSave() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val host = BootSettingsHost(StandardTestDispatcher(testScheduler), object : BootSettings {
            override suspend fun read() = false
            override suspend fun save(enabled: Boolean) {
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        })
        runCurrent()
        host.setEnabled(true)
        runCurrent()
        host.close()
        runCurrent()
        assertTrue(cancelled.isCompleted)
        assertFalse(host.state.value.error)
    }
}
