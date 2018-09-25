package org.samoxive.safetyjim.server

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.User
import org.samoxive.safetyjim.discord.DiscordBot

abstract class AuthenticatedGuildEndpoint(bot: DiscordBot): AuthenticatedEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User): Result {
        val guildId = request.getParam("guildId") ?: return Result(Status.SERVER_ERROR)
        val guild = bot.getGuildFromBot(guildId) ?: return Result(Status.NOT_FOUND)
        val member = guild.getMember(user) ?: return Result(Status.FORBIDDEN)

        return handle(event, request, response, user, guild, member)
    }

    abstract suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result
}