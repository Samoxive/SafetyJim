package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.database.BansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.BanModel
import org.samoxive.safetyjim.server.models.toBanModel
import org.samoxive.safetyjim.tryhard

@Serializable
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
        response.endJson(Json.stringify(GetBansEndpointResponse.serializer(), body))
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

        response.endJson(Json.stringify(BanModel.serializer(), ban.toBanModel(bot)))
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
        val parsedBan = tryhard { Json.parse(BanModel.serializer(), bodyString) }
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
                ban.banTime != newBan.actionTime
        ) {
            return Result(Status.BAD_REQUEST, "Read only properties were modified!")
        }

        if (ban.unbanned) { // expired
            if (!newBan.unbanned || // un-expiring the ban
                    ban.expireTime != newBan.expirationTime) { // changing expiration time
                return Result(Status.BAD_REQUEST, "You can't change expiration property after user has been unbanned.")
            }
        }

        if (ban.moderatorUserId.toString() != newBan.moderatorUser.id) {
            // if original mod isn't in server that's fine, next changes should have a mod in server, next mods must be in server
            // to get permission related information
            val moderator = guild.getMemberById(newBan.moderatorUser.id) ?: return Result(Status.BAD_REQUEST, "Given moderator isn't in the guild!")
            if (!moderator.hasPermission(Permission.BAN_MEMBERS)) {
                return Result(Status.BAD_REQUEST, "Selected moderator isn't privileged enough to issue this action!")
            }
        }

        BansTable.updateBan(ban.copy(
                moderatorUserId = newBan.moderatorUser.id.toLong(),
                expireTime = newBan.expirationTime,
                expires = newBan.expirationTime != 0L,
                unbanned = newBan.unbanned,
                reason = if (newBan.reason.isBlank()) "No reason specified" else newBan.reason
        ))

        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/bans/:banId"
    override val method: HttpMethod = HttpMethod.POST
}
