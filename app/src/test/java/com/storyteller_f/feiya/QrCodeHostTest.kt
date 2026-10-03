package com.storyteller_f.feiya

import android.app.Application
import android.graphics.Bitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QrCodeHostTest {
    @Test fun switchingAddressCancelsOldEncodingAndCloseCancelsWork() = runTest {
        val pending = CompletableDeferred<Bitmap>()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val urls = mutableListOf<String>()
        val host = QrCodeHost(StandardTestDispatcher(testScheduler), "8080", "shares/2",
            addresses = { listOf("127.0.0.1", "192.168.1.2", "10.0.0.2") },
            encode = { url -> urls.add(url); if ("192.168" in url) pending.await() else bitmap },
        )
        runCurrent()
        host.select("10.0.0.2")
        runCurrent()
        assertEquals("http://10.0.0.2:8080/shares/2", host.state.value.url)
        assertSame(bitmap, host.state.value.image)
        pending.complete(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
        runCurrent()
        assertSame(bitmap, host.state.value.image)
        host.close()
        host.select("192.168.1.2")
        runCurrent()
        assertEquals(2, urls.size)
    }

    @Test fun discoveryFailureCanRetryAndEmptyNetworkHasNoInvalidUrl() = runTest {
        var fail = true
        val host = QrCodeHost(StandardTestDispatcher(testScheduler), "9090", "",
            addresses = { if (fail) error("offline") else listOf("127.0.0.1") },
            encode = { error("must not encode without an address") },
        )
        runCurrent()
        assertTrue(host.state.value.failed)
        fail = false
        host.reload()
        runCurrent()
        assertTrue(host.state.value.failed)
        assertEquals("", host.state.value.url)
        host.close()
    }
}
