package com.storyteller_f.feiya

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.storyteller_f.feiya.service.AppService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class DataStoreBootSettings(private val store: DataStore<Preferences>) : BootSettings {
    override suspend fun read() = store.data.first()[START_ON_BOOT] ?: false
    override suspend fun save(enabled: Boolean) {
        store.edit { it[START_ON_BOOT] = enabled }
    }

    private companion object {
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as FeiyaApplication
        handleBoot(
            scope = app.bootScope,
            settings = DataStoreBootSettings(app.dataStore),
            canStart = {
                Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
                    app, Manifest.permission.ACCESS_LOCAL_NETWORK
                ) == PackageManager.PERMISSION_GRANTED
            },
            start = { ContextCompat.startForegroundService(app, Intent(app, AppService::class.java)) },
            finish = { pending.finish() },
            failed = { Log.w("BootReceiver", "Could not start service after boot", it) },
        )
    }
}

/** Complete the broadcast promptly even when storage stalls or Android denies startup. */
internal fun handleBoot(
    scope: CoroutineScope,
    settings: BootSettings,
    canStart: () -> Boolean,
    start: () -> Unit,
    finish: () -> Unit,
    failed: (Exception) -> Unit,
) = scope.launch {
    try {
        withTimeout(8_000) {
            if (settings.read() && canStart()) start()
        }
    } catch (error: TimeoutCancellationException) {
        failed(error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        failed(error)
    } finally {
        finish()
    }
}
