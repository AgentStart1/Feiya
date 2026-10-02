package com.storyteller_f.feiya

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class HidKeyboardState(
    val content: String = "",
    val layout: TargetKeyboardLayout = TargetKeyboardLayout.QWERTY,
    val calibration: KeyboardCalibration = KeyboardCalibration.ANSI,
)

enum class HidKeyboardEffect { UNSUPPORTED_TEXT, SEND_FAILED }

/** Activity-owned keyboard behavior, independent of Android and Compose. */
class HidKeyboardHost(
    coordination: CoroutineDispatcher,
    private val rawKeySender: suspend (HidKey) -> Boolean,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + coordination)
    private val mutableState = MutableStateFlow(HidKeyboardState())
    val state = mutableState.asStateFlow()
    private val effectChannel = Channel<HidKeyboardEffect>(Channel.UNLIMITED)
    val effects = effectChannel.receiveAsFlow()
    private val requests = Channel<List<HidKey>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (keys in requests) {
                val success = try {
                    keys.all { rawKeySender(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!success) effectChannel.send(HidKeyboardEffect.SEND_FAILED)
            }
        }
    }

    fun editContent(content: String) {
        scope.launch { mutableState.value = mutableState.value.copy(content = content) }
    }

    fun selectLayout(layout: TargetKeyboardLayout) {
        scope.launch { mutableState.value = mutableState.value.copy(layout = layout) }
    }

    fun selectCalibration(calibration: KeyboardCalibration) {
        scope.launch { mutableState.value = mutableState.value.copy(calibration = calibration) }
    }

    fun sendContent() {
        scope.launch { enqueueText(mutableState.value.content) }
    }

    fun sendText(text: String) {
        scope.launch { enqueueText(text) }
    }

    private suspend fun enqueueText(text: String) {
        val keys = mutableState.value.layout.mapping.keysFor(text)
        if (keys == null) effectChannel.send(HidKeyboardEffect.UNSUPPORTED_TEXT)
        else requests.send(keys)
    }

    fun sendLeftCalibrationKey() {
        scope.launch { requests.send(listOf(mutableState.value.calibration.leftShiftNeighbor)) }
    }

    fun sendRightCalibrationKey() {
        scope.launch { requests.send(listOf(mutableState.value.calibration.rightShiftNeighbor)) }
    }

    fun sendRawKey(key: HidKey) {
        scope.launch { requests.send(listOf(key)) }
    }

    override fun close() {
        scope.cancel()
        requests.close()
        effectChannel.close()
    }
}
