package org.samoxive.safetyjim

import com.mashape.unirest.request.BaseRequest
import org.slf4j.Logger

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

fun tryAndLog(log: Logger, functionName: String, block: () -> Any) = try {
    block()
} catch (e: Throwable) {
    log.error("Exception occured in $functionName", e)
}

suspend fun <T> await(block: suspend () -> T): T = block()

suspend fun BaseRequest.awaitAsString() = await { asString() }!!