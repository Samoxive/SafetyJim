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
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.SoftbansTable
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.objectMapper
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.SoftbanModel
import org.samoxive.safetyjim.server.models.toSoftbanModel
import org.samoxive.safetyjim.tryhard

data class GetSoftbansEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<SoftbanModel>
)

class GetSoftbansEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val softbans = SoftbansTable.fetchGuildSoftbans(guild, page).map { it.toSoftbanModel(bot) }
        val pageCount = (SoftbansTable.fetchGuildSoftbansCount(guild) / 10) + 1
        val body = GetSoftbansEndpointResponse(page, pageCount, softbans)
        response.endJson(objectMapper.writeValueAsString(body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/softbans"
    override val method: HttpMethod = HttpMethod.GET
}

class GetSoftbanEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val softbanId = request.getParam("softbanId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = softbanId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid softban id!")
        val softban = SoftbansTable.fetchSoftban(id) ?: return Result(Status.NOT_FOUND, "Softban with given id doesn't exist!")

        response.endJson(objectMapper.writeValueAsString(softban.toSoftbanModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/softbans/:softbanId"
    override val method: HttpMethod = HttpMethod.GET
}

class UpdateSoftbanEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        val softbanId = request.getParam("softbanId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = softbanId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid softban id!")
        val softban = SoftbansTable.fetchSoftban(id) ?: return Result(Status.NOT_FOUND, "Softban with given id doesn't exist!")

        val bodyString = event.bodyAsString ?: return Result(Status.BAD_REQUEST)
        val parsedSoftban = tryhard { objectMapper.readValue<SoftbanModel>(bodyString) }
                ?: return Result(Status.BAD_REQUEST)

        val newSoftban = parsedSoftban.copy(
                reason = parsedSoftban.reason.trim()
        )

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            return Result(Status.FORBIDDEN, "You don't have permissions to change softban history!")
        }

        if (softban.guildId != guild.idLong) {
            return Result(Status.FORBIDDEN, "Given softban id doesn't belong to your guild!")
        }

        if (softban.id != newSoftban.id ||
                softban.userId.toString() != newSoftban.user.id ||
                softban.softbanTime != newSoftban.actionTime
        ) {
            return Result(Status.BAD_REQUEST, "Read only properties were modified!")
        }

        if (softban.moderatorUserId.toString() != newSoftban.moderatorUser.id) {
            val moderator = guild.getMemberById(newSoftban.moderatorUser.id) ?: return Result(Status.BAD_REQUEST, "Given moderator isn't in the guild!")
            if (!moderator.hasPermission(Permission.BAN_MEMBERS)) {
                return Result(Status.BAD_REQUEST, "Selected moderator isn't privileged enough to issue this action!")
            }
        }

        if (softban.pardoned && !newSoftban.pardoned) {
            return Result(Status.BAD_REQUEST, "You can't un-pardon a softban!")
        }

        SoftbansTable.updateSoftban(softban.copy(
                moderatorUserId = newSoftban.moderatorUser.id.toLong(),
                reason = if (newSoftban.reason.isBlank()) "No reason specified" else newSoftban.reason,
                pardoned = newSoftban.pardoned
        ))

        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/softbans/:softbanId"
    override val method: HttpMethod = HttpMethod.POST
}
