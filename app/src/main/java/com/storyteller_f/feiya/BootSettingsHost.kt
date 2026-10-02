package com.storyteller_f.feiya

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface BootSettings {
    suspend fun read(): Boolean
    suspend fun save(enabled: Boolean)
}

data class BootSettingsState(
    val enabled: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Boolean = false,
)

/** Owns preference IO and state; the view only renders and forwards actions. */
class BootSettingsHost(dispatcher: CoroutineDispatcher, private val settings: BootSettings) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(BootSettingsState())
    val state = mutableState.asStateFlow()
    private var reading = false

    init { reload() }

    fun reload() {
        scope.launch {
            if (reading || mutableState.value.saving) return@launch
            reading = true
            mutableState.value = mutableState.value.copy(loading = true, error = false)
            try {
                mutableState.value = BootSettingsState(enabled = settings.read(), loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(error = true)
            } finally {
                reading = false
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            val previous = mutableState.value
            if (previous.loading || previous.saving) return@launch
            mutableState.value = previous.copy(saving = true, error = false)
            try {
                settings.save(enabled)
                mutableState.value = BootSettingsState(enabled = enabled, loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = previous.copy(error = true)
            }
        }
    }

    fun close() = scope.cancel()
}
