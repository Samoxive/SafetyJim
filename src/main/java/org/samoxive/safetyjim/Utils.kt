package org.samoxive.safetyjim

import com.mashape.unirest.request.BaseRequest
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.withContext
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

suspend fun <T> await(block: suspend () -> T): T = withContext(DefaultDispatcher, block = block)

suspend fun BaseRequest.awaitAsString() = await { asString() }