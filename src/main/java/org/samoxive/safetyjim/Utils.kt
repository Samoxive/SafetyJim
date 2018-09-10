package org.samoxive.safetyjim

import org.slf4j.Logger

fun <T> tryhard(block: () -> T): T? = try {
    block()
} catch (e: Throwable) {
    null
}

fun tryAndLog(log: Logger, functionName: String, block: () -> Any) = try {
    block()
} catch (e: Throwable) {
    log.error("Exception occured in $functionName", e)
}

