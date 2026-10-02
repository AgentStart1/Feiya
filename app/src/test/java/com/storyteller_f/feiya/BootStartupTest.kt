package com.storyteller_f.feiya

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BootStartupTest {
    private fun settings(enabled: Boolean) = object : BootSettings {
        override suspend fun read() = enabled
        override suspend fun save(enabled: Boolean) = Unit
    }

    @Test fun startsOnlyWhenEnabledAndPermissionAllowsIt() = runTest {
        for (enabled in listOf(false, true)) for (permitted in listOf(false, true)) {
            var starts = 0
            var finishes = 0
            handleBoot(this, settings(enabled), { permitted }, { starts++ }, { finishes++ }, { throw it })
            advanceUntilIdle()
            assertEquals(if (enabled && permitted) 1 else 0, starts)
            assertEquals(1, finishes)
        }
    }

    @Test fun rejectedStartupCompletesBroadcastWithoutRetry() = runTest {
        var finishes = 0
        val errors = mutableListOf<Exception>()
        val rejection = SecurityException("Background start denied")
        handleBoot(this, settings(true), { true }, { throw rejection }, { finishes++ }, errors::add)
        advanceUntilIdle()
        assertEquals(1, finishes)
        assertEquals(1, errors.size)
        assertEquals(rejection.javaClass, errors.single().javaClass)
        assertEquals(rejection.message, errors.single().message)
    }

    @Test fun unreadableSettingsDoNotStartService() = runTest {
        var finishes = 0
        val errors = mutableListOf<Exception>()
        handleBoot(this, object : BootSettings {
            override suspend fun read(): Boolean = throw IOException("unreadable")
            override suspend fun save(enabled: Boolean) = Unit
        }, { true }, { fail("must not start") }, { finishes++ }, errors::add)
        advanceUntilIdle()
        assertEquals(1, finishes)
        assertEquals(1, errors.size)
    }

    @Test fun stalledReadTimesOutAndFinishesBroadcast() = runTest {
        var finishes = 0
        val errors = mutableListOf<Exception>()
        handleBoot(this, object : BootSettings {
            override suspend fun read(): Boolean = awaitCancellation()
            override suspend fun save(enabled: Boolean) = Unit
        }, { true }, { fail("must not start") }, { finishes++ }, errors::add)
        advanceUntilIdle()
        assertEquals(8_000, testScheduler.currentTime)
        assertEquals(1, finishes)
        assertEquals(1, errors.size)
    }

    @Test fun cancellationStillFinishesBroadcast() = runTest {
        var finishes = 0
        val job = handleBoot(this, object : BootSettings {
            override suspend fun read(): Boolean = awaitCancellation()
            override suspend fun save(enabled: Boolean) = Unit
        }, { true }, { fail("must not start") }, { finishes++ }, { throw it })
        runCurrent()
        job.cancel()
        runCurrent()
        assertEquals(1, finishes)
    }
}
