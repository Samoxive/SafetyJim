package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.experimental.async
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.await
import org.samoxive.safetyjim.database.OauthSecret
import org.samoxive.safetyjim.discord.DiscordApi
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.AbstractEndpoint
import org.samoxive.safetyjim.server.AbstractEndpoint.Companion.Status
import org.samoxive.safetyjim.server.AbstractEndpoint.Companion.Result
import org.samoxive.safetyjim.server.endJson
import org.samoxive.safetyjim.server.getJWTFromUserId
import java.util.*

class LoginEndpoint(bot: DiscordBot): AbstractEndpoint(bot) {
    override val route = "/login"
    override val method = HttpMethod.POST

    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse): Result {
        val code = request.getParam("code") ?: return Result(Companion.Status.BAD_REQUEST)
        val secrets = DiscordApi.getUserSecrets(bot.config, code) ?: return Result(Status.BAD_REQUEST)
        val self = DiscordApi.getSelfUser(secrets.access_token) ?: return Result(Status.BAD_REQUEST)

        await {
            transaction {
                val oauthSecret = OauthSecret.findById(self.id)
                if (oauthSecret != null) {
                    oauthSecret.access_token = secrets.access_token
                    oauthSecret.expiration_time = Date().time + secrets.expires_in * 1000
                    oauthSecret.refresh_token = secrets.refresh_token
                } else {
                    OauthSecret.new(self.id) {
                        access_token = secrets.access_token
                        expiration_time = Date().time + secrets.expires_in * 1000
                        refresh_token = secrets.refresh_token
                    }
                }
            }
        }

        val token = getJWTFromUserId(bot.config, self.id)
        response.endJson(token)
        return Result(Status.OK)
    }
}