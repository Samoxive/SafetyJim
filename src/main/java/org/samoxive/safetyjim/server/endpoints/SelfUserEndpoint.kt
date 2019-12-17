package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.getTag
import org.samoxive.safetyjim.objectMapper
import org.samoxive.safetyjim.server.AuthenticatedEndpoint
import org.samoxive.safetyjim.server.Result
import org.samoxive.safetyjim.server.Status
import org.samoxive.safetyjim.server.endJson
import org.samoxive.safetyjim.server.models.SelfUserModel
import org.samoxive.safetyjim.server.models.toGuildModel

class SelfUserEndpoint(bot: DiscordBot) : AuthenticatedEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User): Result {
        val guilds = bot.shards.flatMap { it.jda.guilds }
                .asSequence()
                .filter { it.isMember(user) }
                .map { it.toGuildModel() }
                .toList()
        response.endJson(objectMapper.writeValueAsString(SelfUserModel(user.id, user.getTag(), user.avatarUrl, guilds)))
        return Result(Status.OK)
    }

    override val route: String = "/@me"
    override val method: HttpMethod = HttpMethod.GET
}
