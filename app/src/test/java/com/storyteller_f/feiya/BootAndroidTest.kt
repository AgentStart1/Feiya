package com.storyteller_f.feiya

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.storyteller_f.feiya.service.AppService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FeiyaApplication::class)
class BootAndroidTest {
    private val app get() = RuntimeEnvironment.getApplication() as FeiyaApplication

    private suspend fun broadcast(action: String) {
        app.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
        app.bootScope.coroutineContext[Job]!!.children.toList().joinAll()
    }

    @Test fun defaultOffThenPersistedOnStartsServiceOnlyForNormalBoot() = runTest {
        val settings = DataStoreBootSettings(app.dataStore)
        assertFalse(settings.read())
        broadcast(Intent.ACTION_BOOT_COMPLETED)
        assertNull(shadowOf(app).nextStartedService)
        settings.save(true)
        assertTrue(DataStoreBootSettings(app.dataStore).read())
        broadcast(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        assertNull(shadowOf(app).nextStartedService)
        broadcast(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(AppService::class.java.name, shadowOf(app).nextStartedService.component!!.className)
        settings.save(false)
        broadcast(Intent.ACTION_BOOT_COMPLETED)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun servicePromotesWithoutNotificationsAndLoadsSharesWithoutActivity() = runTest {
        val saved = File(app.filesDir, "saved/boot-example.txt")
        saved.parentFile!!.mkdirs()
        saved.writeText("Shared before reboot")
        val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(notifications).setNotificationsEnabled(false)
        val controller = Robolectric.buildService(AppService::class.java)
        controller.get().stop() // Queue Stop before onCreate, avoiding an automatic listener.
        try {
            controller.create()
            assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
            assertTrue(isUriFilePathInitialised)
            shadowOf(Looper.getMainLooper()).idle()
            val restored = withContext(Dispatchers.IO) {
                withTimeout(5_000) { shares.first { files -> files.any { it.name == saved.name } } }
            }
            assertEquals(saved.name, restored.single().name)
        } finally {
            controller.destroy()
        }
    }
}
