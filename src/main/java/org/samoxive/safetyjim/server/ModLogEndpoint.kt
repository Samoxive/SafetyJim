package org.samoxive.safetyjim.server

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.SettingsTable
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.isStaff

abstract class ModLogEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    private fun canMemberView(member: Member, settings: SettingsEntity): Boolean {
        return when (settings.privacyModLog) {
            SettingsEntity.PRIVACY_EVERYONE -> true
            SettingsEntity.PRIVACY_STAFF_ONLY -> member.isStaff()
            SettingsEntity.PRIVACY_ADMIN_ONLY -> member.hasPermission(Permission.ADMINISTRATOR)
            else -> throw IllegalStateException()
        }
    }

    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        val settings = SettingsTable.getGuildSettings(guild, bot.config)
        if (!canMemberView(member, settings)) {
            return Result(Status.FORBIDDEN, "Server settings prevent you from viewing moderator log entries!")
        }

        return handle(event, request, response, user, guild, member, settings)
    }

    abstract suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result
}