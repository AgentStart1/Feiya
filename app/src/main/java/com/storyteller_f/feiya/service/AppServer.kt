package com.storyteller_f.feiya.service

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.util.filter
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

sealed interface ServerState {
    data object Init : ServerState
    class Started(
        override val port: Int,
        val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>,
        val chatSession: DefaultClientWebSocketSession,
        val client: HttpClient,
        val channel: MutableSharedFlow<SseEvent>,
        val messageList: MutableStateFlow<List<Message>>,
        val time: Long,
    ) : ServerState, BoundServer {
        override suspend fun close() {
            try { chatSession.close() } finally {
                try { client.close() } finally {
                    withContext(Dispatchers.IO) { server.stop() }
                }
            }
        }
    }

    data class Stopped(val reason: String) : ServerState

    data class Error(val cause: Throwable) : ServerState {

        val exceptionMessage = (cause.localizedMessage ?: cause::class.qualifiedName
        ?: cause::class.toString())
    }
}

data class PortSwitchFailure(val requestedPort: Int, val activePort: Int, val cause: Throwable)

class AppServer(service: AppService, private val coordination: CoroutineDispatcher) {
    private val scope = CoroutineScope(service.lifecycleScope.coroutineContext + coordination)
    private val context = service
    val state = MutableStateFlow<ServerState>(ServerState.Init)
    private val ports = ServerBinding(::createServer)
    private val failures = Channel<PortSwitchFailure>(Channel.BUFFERED)
    val portSwitchFailures = failures.receiveAsFlow()

    val messagesCache get() = (state.value as? ServerState.Started)?.messageList?.asStateFlow()

    private suspend fun createServer(port: Int): ServerState.Started {
        val client = httpClient()
        val ready = CompletableDeferred<MutableSharedFlow<SseEvent>>()
        val server = embeddedServer(Netty, port = port, host = AppService.LISTENER_ADDRESS) {
            ready.complete(module(context, client))
        }
        try {
            withContext(Dispatchers.IO) { server.start(wait = false) }
            val channel = ready.await()
            val (session, messages) = withTimeoutOrNull(10_000) { setupSelfClient(port, client) }
                ?: throw IOException("Self WebSocket connection timed out")
            return ServerState.Started(port, server, session, client, channel, messages, System.currentTimeMillis())
        } catch (failure: Throwable) {
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching { client.close() }.exceptionOrNull()?.let(failure::addSuppressed)
                runCatching { server.stop() }.exceptionOrNull()?.let(failure::addSuppressed)
            }
            throw failure
        }
    }

    private suspend fun startInternal(port: Int) {
        try {
            state.value = ports.start(port)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "Unable to start requested server port", failure)
            reportStartFailure(port, failure)
        }
    }

    private suspend fun setupSelfClient(
        port: Int,
        client: HttpClient
    ): Pair<DefaultClientWebSocketSession, MutableStateFlow<List<Message>>> {

        val messagesCache = MutableStateFlow<List<Message>>(emptyList())
        val sessionWaitWorker = CompletableDeferred<DefaultClientWebSocketSession>()
        scope.launch {
            try {
                client.webSocket(
                    method = HttpMethod.Get,
                    host = "127.0.0.1",
                    port = port,
                    path = "/chat"
                ) {
                    sessionWaitWorker.complete(this)
                    while (true) {
                        val message = receiveDeserialized<Message>()
                        messagesCache.value = messagesCache.value + message
                    }
                }
            } catch (cancelled: CancellationException) {
                sessionWaitWorker.completeExceptionally(cancelled)
                throw cancelled
            } catch (failure: Exception) {
                sessionWaitWorker.completeExceptionally(failure)
                Log.e(TAG, "Self WebSocket ended", failure)
            } finally {
                if (!sessionWaitWorker.isCompleted) {
                    sessionWaitWorker.completeExceptionally(IllegalStateException("Self WebSocket closed before connecting"))
                }
            }
        }
        return (sessionWaitWorker.await() to messagesCache)
    }

    private fun httpClient(): HttpClient {
        return HttpClient(CIO) {
            install(WebSockets) {
                pingInterval = 20.seconds
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }
    }

    private suspend fun stopInternal(cause: String) {
        try { ports.stop() } finally { state.value = ServerState.Stopped(cause) }
    }

    private suspend fun stopIfNeed(cause: String) = stopInternal(cause)

    private suspend fun startIfNeed(port: Int) = startInternal(port)

    private suspend fun reportStartFailure(port: Int, cause: Throwable) {
        val active = ports.current
        if (active == null) {
            state.value = ServerState.Error(cause)
        } else {
            state.value = active
            failures.send(PortSwitchFailure(port, active.port, cause))
        }
    }

    suspend fun onReceiveEventPort(port: Int, event: Int?) = withContext(coordination) {
        if (event != null) {
            when (event) {
                AppService.EVENT_STOP -> {
                    //stop server
                    stopIfNeed("stop event.")
                }

                AppService.EVENT_RESTART -> {
                    Log.i(TAG, "onReceiveEventPort: restart")
                    stopIfNeed("restart event.")
                    startIfNeed(port)
                }
            }
        } else {
            when {
                port in (AppService.VALID_PORT + 1)..65535 -> {
                    //start server
                    startIfNeed(port)
                }

                else -> {
                    val cause = IllegalAccessException("invalid port $port")
                    reportStartFailure(port, cause)
                }
            }
        }

    }


    fun stopBlocking() {
        // Android's onDestroy is synchronous; join cleanup before returning to the framework.
        runBlocking { withContext(coordination) { stopInternal("service stopped") } }
    }

    suspend fun sendMessage(content: String) {
        val serverState = state.value
        if (serverState is ServerState.Started) {
            val session = serverState.chatSession
            session.send(content)
        }
    }

    suspend fun emitRefreshEvent() {
        println("emitRefreshEvent")
        val serverState = state.value
        if (serverState is ServerState.Started) {
            serverState.channel.emit(
                SseEvent(
                    "refresh",
                    "message",
                    System.currentTimeMillis().toString()
                )
            )
        }
    }


    companion object {
        private const val TAG = "AppServer"
    }
}

fun Application.module(
    context: Context,
    client: HttpClient,
): MutableSharedFlow<SseEvent> {
    plugPlugins()
    val events = setupSse()
    configureRouting(context)
    webSocketsService()
    setupAvatarProxy(client)
    return events
}

private val avatarPattern = Regex("/avatar/(\\w+).png")

/**
 * 请求url
 * http://localhost:80080/avatar/user1.png
 */
private fun Application.setupAvatarProxy(client: HttpClient) {
    // Let's intercept all the requests at the [ApplicationCallPipeline.Call] phase.
    intercept(ApplicationCallPipeline.Call) {
        val uri = call.request.uri
        avatarPattern.find(uri).runCatching {
            this?.groups?.get(1)!!.value
        }.onSuccess { pngName ->
            // We create a GET request to the wikipedia domain and return the call (with the request and the unprocessed response).
            val response =
                client.request(getAvatarIcon(pngName))

            // Get the relevant headers of the client response.
            val proxiedHeaders = response.headers
            val contentType = proxiedHeaders[HttpHeaders.ContentType]
            val contentLength = proxiedHeaders[HttpHeaders.ContentLength]

            call.respond(object : OutgoingContent.WriteChannelContent() {
                override val contentLength: Long? = contentLength?.toLong()
                override val contentType: ContentType? =
                    contentType?.let { ContentType.parse(it) }
                override val headers: Headers = Headers.build {
                    appendAll(proxiedHeaders.filter { key, _ ->
                        !key.equals(
                            HttpHeaders.ContentType,
                            ignoreCase = true
                        ) && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                    })
                }
                override val status: HttpStatusCode = response.status
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    response.bodyAsChannel().copyAndClose(channel)
                }
            })
        }.onFailure {
            proceed()
        }

    }
}