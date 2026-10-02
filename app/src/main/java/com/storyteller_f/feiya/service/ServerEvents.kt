package com.storyteller_f.feiya.service

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow

private sealed interface ServerEvent {
    data class Port(val value: Int) : ServerEvent
    data class Command(val value: Int) : ServerEvent
}

/** One consumer serializes port changes and commands; commands are consumed exactly once. */
internal suspend fun collectServerEvents(
    ports: Flow<Int>,
    commands: ReceiveChannel<Int>,
    receive: suspend (port: Int, command: Int?) -> Unit,
) {
    var currentPort = ports.first()
    // A command queued before initialization takes
    // precedence over automatic startup, and is removed from the queue.
    receive(currentPort, commands.tryReceive().getOrNull())
    merge(
        ports.map { ServerEvent.Port(it) },
        commands.receiveAsFlow().map { ServerEvent.Command(it) },
    ).collect { event ->
        when (event) {
            is ServerEvent.Port -> if (event.value != currentPort) {
                currentPort = event.value
                receive(currentPort, null)
            }
            is ServerEvent.Command -> receive(currentPort, event.value)
        }
    }
}
