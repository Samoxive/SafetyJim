package org.samoxive.safetyjim

import io.vertx.core.Vertx
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions

lateinit var vertx: Vertx
lateinit var httpClient: WebClient

fun initHttpClient() {
    vertx = Vertx.vertx()
    httpClient = WebClient.create(vertx, WebClientOptions().setUserAgent("Safety Jim").setSsl(true))
}

fun <T> tryhard(block: () -> T): T? = try {
    block()
} catch (e: Throwable) {
    null
}

suspend fun <T> tryhardAsync(block: suspend () -> T): T? = try {
    block()
} catch (e: Throwable) {
    null
}
