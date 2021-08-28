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
import org.samoxive.safetyjim.database.WarnsTable
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.objectMapper
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.WarnModel
import org.samoxive.safetyjim.server.models.toWarnModel
import org.samoxive.safetyjim.tryhard

data class GetWarnsEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<WarnModel>
)

class GetWarnsEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val warns = WarnsTable.fetchGuildWarns(guild, page).map { it.toWarnModel(bot) }
        val pageCount = (WarnsTable.fetchGuildWarnsCount(guild) / 10) + 1
        val body = GetWarnsEndpointResponse(page, pageCount, warns)
        response.endJson(objectMapper.writeValueAsString(body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/warns"
    override val method: HttpMethod = HttpMethod.GET
}

class GetWarnEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val warnId = request.getParam("warnId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = warnId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid warn id!")
        val warn = WarnsTable.fetchWarn(id) ?: return Result(Status.NOT_FOUND, "Warn with given id doesn't exist!")

        response.endJson(objectMapper.writeValueAsString(warn.toWarnModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/warns/:warnId"
    override val method: HttpMethod = HttpMethod.GET
}

class UpdateWarnEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        val warnId = request.getParam("warnId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = warnId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid warn id!")
        val warn = WarnsTable.fetchWarn(id) ?: return Result(Status.NOT_FOUND, "Warn with given id doesn't exist!")

        val bodyString = event.bodyAsString ?: return Result(Status.BAD_REQUEST)
        val parsedWarn = tryhard { objectMapper.readValue<WarnModel>(bodyString) }
            ?: return Result(Status.BAD_REQUEST)

        val newWarn = parsedWarn.copy(
            reason = parsedWarn.reason.trim()
        )

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            return Result(Status.FORBIDDEN, "You don't have permissions to change warn history!")
        }

        if (warn.guildId != guild.idLong) {
            return Result(Status.FORBIDDEN, "Given warn id doesn't belong to your guild!")
        }

        if (warn.id != newWarn.id ||
            warn.userId.toString() != newWarn.user.id ||
            warn.warnTime != newWarn.actionTime ||
            warn.moderatorUserId.toString() != newWarn.moderatorUser.id
        ) {
            return Result(Status.BAD_REQUEST, "Read only properties were modified!")
        }

        if (warn.pardoned && !newWarn.pardoned) {
            return Result(Status.BAD_REQUEST, "You can't un-pardon a warn!")
        }

        WarnsTable.updateWarn(
            warn.copy(
                reason = newWarn.reason.ifBlank { "No reason specified" },
                pardoned = newWarn.pardoned
            )
        )

        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/warns/:warnId"
    override val method: HttpMethod = HttpMethod.POST
}
