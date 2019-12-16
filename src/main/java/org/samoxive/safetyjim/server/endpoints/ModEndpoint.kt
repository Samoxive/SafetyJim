package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.UserModel
import org.samoxive.safetyjim.server.models.toUserModel

data class GetModsResponse(
    val banMods: List<UserModel>,
    val kickMods: List<UserModel>,
    val roleMods: List<UserModel>
)

class GetModsEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        val body = GetModsResponse(
                guild.members.filter { it.hasPermission(Permission.BAN_MEMBERS) }.map { it.user.toUserModel() },
                guild.members.filter { it.hasPermission(Permission.KICK_MEMBERS) }.map { it.user.toUserModel() },
                guild.members.filter { it.hasPermission(Permission.MANAGE_ROLES) }.map { it.user.toUserModel() }
        )

        response.endJson(objectMapper.writeValueAsString(body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/mods"
    override val method: HttpMethod = HttpMethod.GET
}
