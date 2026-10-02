package com.storyteller_f.feiya

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

class FeiyaApplication : Application() {
    val settingsCoordination = Dispatchers.Default.limitedParallelism(1)
    val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        uriFilePath = File(filesDir, "list.txt").absolutePath
    }

    val serverCoordination = Dispatchers.Default.limitedParallelism(1)
    val keyboardCoordination = Dispatchers.Default.limitedParallelism(1)
}
