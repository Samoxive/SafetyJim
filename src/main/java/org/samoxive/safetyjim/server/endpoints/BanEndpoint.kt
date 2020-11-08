package org.samoxive.safetyjim.server.endpoints

import com.fasterxml.jackson.module.kotlin.readValue
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.database.BansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.objectMapper
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.BanModel
import org.samoxive.safetyjim.server.models.toBanModel
import org.samoxive.safetyjim.tryhard

data class GetBansEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<BanModel>
)

class GetBansEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val bans = BansTable.fetchGuildBans(guild, page).map { it.toBanModel(bot) }
        val pageCount = (BansTable.fetchGuildBansCount(guild) / 10) + 1
        val body = GetBansEndpointResponse(page, pageCount, bans)
        response.endJson(objectMapper.writeValueAsString(body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/bans"
    override val method: HttpMethod = HttpMethod.GET
}

class GetBanEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val banId = request.getParam("banId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = banId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid ban id!")
        val ban = BansTable.fetchBan(id) ?: return Result(Status.NOT_FOUND, "Ban with given id doesn't exist!")

        response.endJson(objectMapper.writeValueAsString(ban.toBanModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/bans/:banId"
    override val method: HttpMethod = HttpMethod.GET
}

// Ban states: Issued-Permanent, Issued-Temporary, Expired (unbanned)
// Expired bans can only have their moderator and reason changed
// Issued-Temporary bans
class UpdateBanEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        val banId = request.getParam("banId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = banId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid ban id!")
        val ban = BansTable.fetchBan(id) ?: return Result(Status.NOT_FOUND, "Ban with given id doesn't exist!")

        val bodyString = event.bodyAsString ?: return Result(Status.BAD_REQUEST)
        val parsedBan = tryhard { objectMapper.readValue<BanModel>(bodyString) }
            ?: return Result(Status.BAD_REQUEST)

        val newBan = parsedBan.copy(
            reason = parsedBan.reason.trim()
        )

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            return Result(Status.FORBIDDEN, "You don't have permissions to change ban history!")
        }

        if (ban.guildId != guild.idLong) {
            return Result(Status.FORBIDDEN, "Given ban id doesn't belong to your guild!")
        }

        if (ban.id != newBan.id ||
            ban.userId.toString() != newBan.user.id ||
            ban.banTime != newBan.actionTime ||
            ban.moderatorUserId.toString() != newBan.moderatorUser.id
        ) {
            return Result(Status.BAD_REQUEST, "Read only properties were modified!")
        }

        if (ban.unbanned) { // expired
            if (!newBan.unbanned || // un-expiring the ban
                ban.expireTime != newBan.expirationTime
            ) { // changing expiration time
                return Result(Status.BAD_REQUEST, "You can't change expiration property after user has been unbanned.")
            }
        }

        BansTable.updateBan(
            ban.copy(
                expireTime = newBan.expirationTime,
                expires = newBan.expirationTime != 0L,
                unbanned = newBan.unbanned,
                reason = if (newBan.reason.isBlank()) "No reason specified" else newBan.reason
            )
        )

        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/bans/:banId"
    override val method: HttpMethod = HttpMethod.POST
}
