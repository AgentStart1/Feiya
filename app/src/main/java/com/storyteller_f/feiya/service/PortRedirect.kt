package com.storyteller_f.feiya.service

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.coroutines.flow.StateFlow

fun Application.installPortRedirect(target: StateFlow<Int?>) {
    intercept(ApplicationCallPipeline.Setup) {
        target.value?.let { port ->
            val authority = call.request.headers[HttpHeaders.Host] ?: call.request.local.serverHost
            val destination = URLBuilder("http://$authority").apply {
                this.port = port
            }.buildString().removeSuffix("/") + call.request.uri
            call.response.header(HttpHeaders.Location, destination)
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(HttpStatusCode.TemporaryRedirect)
            finish()
        }
    }
}
