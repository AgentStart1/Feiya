package com.storyteller_f.feiya.service

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface BoundServer {
    val port: Int
    suspend fun close()
}

/** Serialized by the service event collector; only the current listener is retained. */
class ServerBinding<T : BoundServer>(private val bind: suspend (Int) -> T) {
    var current: T? = null
        private set

    suspend fun start(port: Int): T {
        require(port in 1001..65535) { "Invalid server port" }
        current?.takeIf { it.port == port }?.let { return it }
        // Keep the current server functional if the replacement cannot start.
        val next = bind(port)
        withContext(NonCancellable) {
            val previous = current
            current = next
            previous?.close()
        }
        return next
    }

    suspend fun stop() {
        withContext(NonCancellable) {
            val previous = current
            current = null
            previous?.close()
        }
    }
}
