package com.storyteller_f.feiya

import io.ktor.http.ContentType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class UriStreamTest {
    @Test fun cancellingAnInFlightBodyClosesItsStream() = runTest {
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val content = UriFileContent(ContentType.Application.OctetStream, null, null, this) {
            object : ByteArrayInputStream(ByteArray(1_000_000)) {
                init { opened.complete(Unit) }
                override fun close() { super.close(); closed.complete(Unit) }
            }
        }
        val body = content.readFrom()
        opened.await()
        body.cancel(null)
        closed.await()
        assertTrue(closed.isCompleted)
    }
}
