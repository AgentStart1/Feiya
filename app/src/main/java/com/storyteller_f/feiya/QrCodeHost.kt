package com.storyteller_f.feiya

import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coordinates network discovery and QR encoding away from Compose rendering. */
data class QrCodeState(val addresses: List<String> = emptyList(), val address: String = "", val url: String = "", val image: Bitmap? = null, val failed: Boolean = false)

class QrCodeHost(
    coordination: CoroutineDispatcher,
    private val port: String,
    private val path: String,
    private val addresses: suspend () -> List<String> = ::allIp,
    private val encode: suspend (String) -> Bitmap = { url -> withContext(Dispatchers.Default) { url.createQRImage(480, 480) } },
) {
    private val scope = CoroutineScope(SupervisorJob() + coordination)
    private val mutableState = MutableStateFlow(QrCodeState())
    val state = mutableState.asStateFlow()
    private var render: Job? = null
    private var discovery: Job? = null
    init { reload() }

    fun reload() {
        scope.launch {
            discovery?.cancel()
            render?.cancel()
            mutableState.value = QrCodeState()
            discovery = scope.launch {
                try {
                    val available = addresses().filterNot { it.contains(':') || it.startsWith("127.") || it == "0.0.0.0" }.distinct()
                    ensureActive()
                    val selected = available.firstOrNull { it.startsWith("192.168.") } ?: available.firstOrNull().orEmpty()
                    mutableState.value = QrCodeState(addresses = available, address = selected)
                    if (selected.isNotEmpty()) render(selected) else mutableState.value = mutableState.value.copy(failed = true)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutableState.value = mutableState.value.copy(failed = true) }
            }
        }
    }
    fun select(address: String) { scope.launch { if (address in state.value.addresses) render(address) } }
    private fun render(address: String) {
        render?.cancel()
        val url = "http://$address:$port/$path"
        mutableState.value = mutableState.value.copy(address = address, url = url, image = null, failed = false)
        render = scope.launch {
            try {
                val image = encode(url)
                ensureActive()
                mutableState.value = mutableState.value.copy(image = image)
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(failed = true) }
        }
    }
    fun close() { scope.cancel() }
}
