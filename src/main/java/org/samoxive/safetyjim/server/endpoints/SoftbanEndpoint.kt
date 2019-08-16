package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.list
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.SoftbansTable
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.ModLogEndpoint
import org.samoxive.safetyjim.server.Result
import org.samoxive.safetyjim.server.Status
import org.samoxive.safetyjim.server.endJson
import org.samoxive.safetyjim.server.models.SoftbanModel
import org.samoxive.safetyjim.server.models.toSoftbanModel

class GetSoftbansEndpoint(bot: DiscordBot): ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val softbans = SoftbansTable.fetchGuildSoftbans(guild).map { it.toSoftbanModel(bot) }
        response.endJson(Json.stringify(SoftbanModel.serializer().list, softbans))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/softbans"
    override val method: HttpMethod = HttpMethod.GET
}