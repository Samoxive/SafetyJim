package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.core.entities.User
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.getTag
import org.samoxive.safetyjim.server.AuthenticatedEndpoint
import org.samoxive.safetyjim.server.endJson
import org.samoxive.safetyjim.server.entities.GuildEntity
import org.samoxive.safetyjim.server.entities.SelfUserEntity

class SelfUserEndpoint(bot: DiscordBot) : AuthenticatedEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User): Companion.Result {
        val guilds = bot.shards.flatMap { it.jda.guilds }
                .filter { it.isMember(user) }
                .map { GuildEntity(it.id, it.name, it.iconUrl) }
        response.endJson(SelfUserEntity(user.id, user.getTag(), user.avatarUrl, guilds))
        return Companion.Result(Companion.Status.OK)
    }

    override val route: String = "/@me"
    override val method: HttpMethod = HttpMethod.GET
}