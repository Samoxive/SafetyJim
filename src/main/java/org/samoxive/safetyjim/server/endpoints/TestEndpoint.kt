package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.core.entities.User
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.AuthenticatedEndpoint
import org.samoxive.safetyjim.server.endJson
import org.samoxive.safetyjim.server.AbstractEndpoint.Companion.Status
import org.samoxive.safetyjim.server.AbstractEndpoint.Companion.Result

class TestEndpoint(bot: DiscordBot): AuthenticatedEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User): Companion.Result {
        response.endJson(user.toString())
        return Result(Status.OK)
    }

    override val route: String = "/test"
    override val method: HttpMethod = HttpMethod.GET
}