package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.AbstractEndpoint
import org.samoxive.safetyjim.server.Result
import org.samoxive.safetyjim.server.Status

class TestEndpoint(bot: DiscordBot) : AbstractEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse): Result {
        throw Exception("oops")
        return Result(Status.OK)
    }

    override val route: String = "/test"
    override val method: HttpMethod = HttpMethod.GET
}