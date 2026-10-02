package com.storyteller_f.feiya

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes complete press/release pairs, including shortcuts and calibration. */
class RawHidKeyboard(private val pause: suspend (Long) -> Unit = { delay(it) }) {
    private val mutex = Mutex()

    suspend fun sendRawKey(
        key: HidKey,
        canSend: () -> Boolean = { true },
        report: (ByteArray) -> Boolean,
    ): Boolean = mutex.withLock {
        if (!canSend()) return@withLock false
        val released: Boolean
        val pressed = try {
            report(byteArrayOf(key.modifier.toByte(), key.usage.toByte())).also {
                if (it) pause(100)
            }
        } finally {
            // Synchronous cleanup also runs when cancellation interrupts the key-down delay.
            released = report(byteArrayOf(0, 0))
        }
        if (pressed && released) pause(100)
        pressed && released
    }
}
