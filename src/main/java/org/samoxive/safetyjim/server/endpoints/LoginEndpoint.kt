package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import org.samoxive.safetyjim.discord.DiscordApi
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*

class LoginEndpoint(bot: DiscordBot) : AbstractEndpoint(bot) {
    override val route = "/login"
    override val method = HttpMethod.POST

    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse): Result {
        val code = request.getParam("code") ?: return Result(Status.BAD_REQUEST)
        val secrets = DiscordApi.getUserSecrets(bot.config, code) ?: return Result(Status.BAD_REQUEST)
        val self = DiscordApi.getSelfUser(secrets.access_token) ?: return Result(Status.BAD_REQUEST)

        val token = createJWTFromUserId(bot.config, self.id)
        response.endJson(token)
        return Result(Status.OK)
    }
}