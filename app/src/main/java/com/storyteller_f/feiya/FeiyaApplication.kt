package com.storyteller_f.feiya

import android.app.Application
import kotlinx.coroutines.Dispatchers

class FeiyaApplication : Application() {
    val keyboardCoordination = Dispatchers.Default.limitedParallelism(1)
}
