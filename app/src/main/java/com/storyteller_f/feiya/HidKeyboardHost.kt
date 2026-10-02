package com.storyteller_f.feiya

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class HidKeyboardState(
    val layout: TargetKeyboardLayout = TargetKeyboardLayout.QWERTY,
    val calibration: KeyboardCalibration = KeyboardCalibration.ANSI,
    val tasks: List<HidSendTask> = emptyList(),
)

enum class HidKeyboardEffect { UNSUPPORTED_TEXT, SEND_FAILED, CONNECTION_CHANGED, SENT }

/** Activity-owned keyboard behavior; editing/IME state stays in the UI. */
class HidKeyboardHost(
    coordination: CoroutineDispatcher,
    private val connections: StateFlow<HidKeyboardConnection?>,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + coordination)
    private val mutableState = MutableStateFlow(HidKeyboardState())
    val state = mutableState.asStateFlow()
    private val effectChannel = Channel<HidKeyboardEffect>(Channel.UNLIMITED)
    val effects = effectChannel.receiveAsFlow()
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private var nextId = 1L
    private var active: Pair<Long, Job>? = null

    private data class Request(val id: Long, val keys: List<HidKey>, val connection: HidKeyboardConnection)

    init {
        scope.launch {
            try {
                for (request in requests) {
                    if (task(request.id)?.status != HidTaskStatus.QUEUED) continue
                    coroutineScope {
                        val job = launch(start = CoroutineStart.LAZY) { send(request) }
                        active = request.id to job
                        job.start()
                        job.join()
                        active = null
                    }
                }
            } finally {
                // Closing the activity cancels active work and terminalizes queued tasks.
                publishTasks(mutableState.value.tasks.map {
                    if (it.status.isFinished) it else it.copy(status = HidTaskStatus.CANCELLED)
                })
                requests.close()
                effectChannel.close()
            }
        }
    }

    fun selectLayout(layout: TargetKeyboardLayout) {
        scope.launch { mutableState.value = mutableState.value.copy(layout = layout) }
    }

    fun selectCalibration(calibration: KeyboardCalibration) {
        scope.launch { mutableState.value = mutableState.value.copy(calibration = calibration) }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        // Capture synchronously: a delayed dispatcher must not bind this action to a later connection.
        val connection = connections.value
        scope.launch {
            enqueue(HidTaskKind.TEXT, mutableState.value.layout.mapping.keysFor(text), connection)
        }
    }

    fun sendLeftCalibrationKey() {
        val connection = connections.value
        scope.launch {
            enqueue(HidTaskKind.CALIBRATION, listOf(mutableState.value.calibration.leftShiftNeighbor), connection)
        }
    }

    fun sendRightCalibrationKey() {
        val connection = connections.value
        scope.launch {
            enqueue(HidTaskKind.CALIBRATION, listOf(mutableState.value.calibration.rightShiftNeighbor), connection)
        }
    }

    fun sendRawKey(key: HidKey) {
        val connection = connections.value
        scope.launch { enqueue(HidTaskKind.RAW_KEY, listOf(key), connection) }
    }

    fun cancelTask(id: Long) {
        scope.launch { cancel(id) }
    }

    fun cancelAll() {
        scope.launch {
            mutableState.value.tasks.filter { it.status.canCancel }.forEach { cancel(it.id) }
        }
    }

    private fun cancel(id: Long) {
        when (task(id)?.status) {
            HidTaskStatus.QUEUED -> updateTask(id) { it.copy(status = HidTaskStatus.CANCELLED) }
            HidTaskStatus.SENDING -> {
                updateTask(id) { it.copy(status = HidTaskStatus.CANCELLING) }
                active?.takeIf { it.first == id }?.second?.cancel()
            }
            else -> Unit
        }
    }

    private fun enqueue(kind: HidTaskKind, keys: List<HidKey>?, connection: HidKeyboardConnection?) {
        val id = nextId++
        publishTasks(mutableState.value.tasks + HidSendTask(id, kind, connection?.deviceName, keys?.size ?: 0))
        when {
            keys == null -> fail(id, HidTaskFailure.UNSUPPORTED_TEXT)
            connection == null -> fail(id, HidTaskFailure.NOT_CONNECTED)
            !isCurrent(connection) -> fail(id, HidTaskFailure.CONNECTION_CHANGED)
            else -> requests.trySend(Request(id, keys, connection))
        }
    }

    private suspend fun send(request: Request) {
        if (task(request.id)?.status != HidTaskStatus.QUEUED) return
        try {
            updateTask(request.id) { it.copy(status = HidTaskStatus.SENDING) }
            for (key in request.keys) {
                if (!isCurrent(request.connection)) {
                    fail(request.id, HidTaskFailure.CONNECTION_CHANGED)
                    return
                }
                val result = request.connection.sendRawKey(key)
                currentCoroutineContext().ensureActive()
                when {
                    !isCurrent(request.connection) || result == HidKeyResult.CONNECTION_CHANGED -> {
                        fail(request.id, HidTaskFailure.CONNECTION_CHANGED)
                        return
                    }
                    result == HidKeyResult.FAILED -> {
                        fail(request.id, HidTaskFailure.SEND_FAILED)
                        return
                    }
                    else -> updateTask(request.id) { it.copy(sentKeys = it.sentKeys + 1) }
                }
            }
            updateTask(request.id) { it.copy(status = HidTaskStatus.SENT) }
            effectChannel.trySend(HidKeyboardEffect.SENT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fail(request.id, if (isCurrent(request.connection)) HidTaskFailure.SEND_FAILED else HidTaskFailure.CONNECTION_CHANGED)
        } finally {
            if (task(request.id)?.status?.isFinished == false) {
                updateTask(request.id) { it.copy(status = HidTaskStatus.CANCELLED) }
            }
        }
    }

    private fun isCurrent(connection: HidKeyboardConnection): Boolean =
        connections.value === connection && connection.isConnected

    private fun fail(id: Long, failure: HidTaskFailure) {
        updateTask(id) { it.copy(status = HidTaskStatus.FAILED, failure = failure) }
        effectChannel.trySend(when (failure) {
            HidTaskFailure.UNSUPPORTED_TEXT -> HidKeyboardEffect.UNSUPPORTED_TEXT
            HidTaskFailure.CONNECTION_CHANGED -> HidKeyboardEffect.CONNECTION_CHANGED
            else -> HidKeyboardEffect.SEND_FAILED
        })
    }

    private fun task(id: Long) = mutableState.value.tasks.firstOrNull { it.id == id }

    private fun updateTask(id: Long, update: (HidSendTask) -> HidSendTask) {
        publishTasks(mutableState.value.tasks.map { if (it.id == id) update(it) else it })
    }

    private fun publishTasks(tasks: List<HidSendTask>) {
        val retained = tasks.filter { it.status.isFinished }.takeLast(10).map { it.id }.toSet()
        mutableState.value = mutableState.value.copy(tasks = tasks.filter { !it.status.isFinished || it.id in retained })
    }

    override fun close() {
        scope.cancel()
    }
}
