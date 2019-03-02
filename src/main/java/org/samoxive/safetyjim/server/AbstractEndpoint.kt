package org.samoxive.safetyjim.server

import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.samoxive.safetyjim.discord.DiscordBot
import org.slf4j.LoggerFactory

enum class Status(val code: Int) {
    OK(200),
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    SERVER_ERROR(500)
}

data class Result(val status: Status, val message: String = "")

private val logger = LoggerFactory.getLogger("Jim Endpoint")

abstract class AbstractEndpoint(val bot: DiscordBot) : Handler<RoutingContext> {
    override fun handle(event: RoutingContext) {
        GlobalScope.launch {
            val request = event.request()
            val response = event.response()

            val result = try {
                handle(event, request, response)
            } catch (e: Throwable) {
                logger.error("$method - $route", e)
                Result(Status.SERVER_ERROR, "Server error, please retry later.")
            }

            if (result.status == Status.OK) {
                return@launch
            }

            if (result.message != "") {
                response.statusCode = result.status.code
                response.putHeader("Content-Type", "application/json")
                response.end("\"${result.message}\"")
            } else {
                response.statusCode = result.status.code
                response.end()
            }
        }
    }

    abstract val route: String
    abstract val method: HttpMethod
    abstract suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse): Result
}