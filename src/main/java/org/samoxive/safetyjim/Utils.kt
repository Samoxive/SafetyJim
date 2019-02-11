package org.samoxive.safetyjim

import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.JsonNode
import com.mashape.unirest.http.async.Callback
import com.mashape.unirest.http.exceptions.UnirestException
import com.mashape.unirest.request.BaseRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

suspend fun <T> await(block: suspend () -> T): T = block()

suspend fun BaseRequest.awaitAsString(): HttpResponse<String> = suspendCoroutine { cont ->
    asStringAsync(object: Callback<String> {
        override fun cancelled() {
            throw NotImplementedError("don't cancel requests, we don't need to")
        }

        override fun completed(response: HttpResponse<String>) {
            cont.resume(response)
        }

        override fun failed(e: UnirestException) {
            cont.resumeWithException(e)
        }
    })
}

suspend fun BaseRequest.tryAwaitAsString(): HttpResponse<String>? = tryhardAsync {
    awaitAsString()
}

suspend fun BaseRequest.awaitAsJSON(): HttpResponse<JsonNode> = suspendCoroutine { cont ->
    asJsonAsync(object: Callback<JsonNode> {
        override fun cancelled() {
            throw NotImplementedError("don't cancel requests, we don't need to")
        }

        override fun completed(response: HttpResponse<JsonNode>) {
            cont.resume(response)
        }

        override fun failed(e: UnirestException) {
            cont.resumeWithException(e)
        }
    })
}

suspend fun BaseRequest.tryAwaitAsJSON(): HttpResponse<JsonNode>? = tryhardAsync {
    awaitAsJSON()
}