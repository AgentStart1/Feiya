package com.storyteller_f.feiya

import com.storyteller_f.feiya.service.SharedFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FeiyaApplication::class)
class SharedUrisTest {
    private val context get() = RuntimeEnvironment.getApplication() as FeiyaApplication

    private lateinit var original: ByteArray
    private var previousShares = emptyList<SharedFileInfo>()

    @Before fun isolateBuffer() {
        context // Initialize the application before accessing its mapped buffer.
        original = ByteArray(savedUriFile.capacity()).also { savedUriFile.duplicate().apply { position(0) }.get(it) }
        previousShares = shares.value
        savedUriFile.position(0)
        savedUriFile.put(0.toByte())
    }

    @After fun restoreBuffer() {
        savedUriFile.position(0)
        savedUriFile.put(original)
        savedUriFile.force()
        shares.value = previousShares
    }

    private fun persistedText(): String = ByteArray(savedUriFile.capacity())
        .also { savedUriFile.duplicate().apply { position(0) }.get(it) }
        .decodeToString().substringBefore('\u0000')

    @Test fun repeatedConcurrentAddsProduceOnePersistedEntry() = runTest {
        val uri = "file:///test.zip"
        List(8) { async(Dispatchers.Default) { savedUriFile.appendText(uri) } }.awaitAll()
        context.cacheInvalid()
        assertEquals(listOf(uri), shares.value.map { it.uri })
        assertEquals(uri, persistedText())
    }

    @Test fun legacyDuplicatesAreRepairedAndRemovingFixturePreservesOtherShares() = runTest {
        val fixture = "file:///fixture.zip"
        val existing = "file:///existing.zip"
        withContext(Dispatchers.IO) {
            savedUriFile.position(0)
            savedUriFile.put("$existing\n$fixture\n$fixture\n$existing\u0000".toByteArray())
        }
        context.cacheInvalid()
        assertEquals(listOf(existing, fixture), shares.value.map { it.uri })
        assertEquals("$existing\n$fixture", persistedText())
        context.removeUri(SharedFileInfo(fixture, "fixture.zip"))
        context.cacheInvalid()
        assertEquals(listOf(existing), shares.value.map { it.uri })
        assertEquals(existing, persistedText())
    }
}
