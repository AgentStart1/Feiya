package com.storyteller_f.feiya

import android.app.Application
import kotlinx.coroutines.Dispatchers

class FeiyaApplication : Application() {
    val serverCoordination = Dispatchers.Default.limitedParallelism(1)
    val keyboardCoordination = Dispatchers.Default.limitedParallelism(1)
}
