package com.storyteller_f.feiya.service

/** A bound server can remain available as a redirect after another port becomes active. */
interface PortEndpoint {
    val port: Int
    fun redirectTo(port: Int?)
    suspend fun close()
}

/** Calls are serialized by the service's event collector. Bind before retiring the old port. */
class PortHandoff<T : PortEndpoint>(private val bind: suspend (Int) -> T) {
    var current: T? = null
        private set
    private val previous = mutableMapOf<Int, T>()

    suspend fun start(port: Int): T {
        require(port in 1001..65535) { "Invalid server port" }
        current?.takeIf { it.port == port }?.let { return it }
        // If binding fails, every existing endpoint retains its previous role.
        val next = previous[port] ?: bind(port)
        previous.remove(port)
        current?.let { previous[it.port] = it }
        next.redirectTo(null)
        previous.values.forEach { it.redirectTo(port) }
        current = next
        return next
    }

    suspend fun stop() {
        val endpoints = previous.values.toList() + listOfNotNull(current)
        previous.clear()
        current = null
        val failures = endpoints.mapNotNull { endpoint ->
            runCatching { endpoint.close() }.exceptionOrNull()
        }
        failures.firstOrNull()?.let { throw it }
    }
}
