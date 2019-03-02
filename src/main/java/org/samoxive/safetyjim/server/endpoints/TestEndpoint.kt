package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.core.entities.User
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*

class TestEndpoint(bot: DiscordBot) : AbstractEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse): Result {
        throw Exception("oops")
        return Result(Status.OK)
    }

    override val route: String = "/test"
    override val method: HttpMethod = HttpMethod.GET
}