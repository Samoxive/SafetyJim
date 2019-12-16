package org.samoxive.safetyjim.server

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.endpoints.*
import org.slf4j.LoggerFactory

class Server(val bot: DiscordBot, vertx: Vertx) {
    private val endpoints = listOf(
        LoginEndpoint(bot),
        SelfUserEndpoint(bot),
        GetGuildSettingsEndpoint(bot),
        PostGuildSettingsEndpoint(bot),
        ResetGuildSettingsEndpoint(bot),
        CaptchaPageEndpoint(bot),
        CaptchaSubmitEndpoint(bot),
        GetBansEndpoint(bot),
        GetSoftbansEndpoint(bot),
        GetHardbansEndpoint(bot),
        GetKicksEndpoint(bot),
        GetMutesEndpoint(bot),
        GetWarnsEndpoint(bot),
        GetBanEndpoint(bot),
        GetSoftbanEndpoint(bot),
        GetHardbanEndpoint(bot),
        GetKickEndpoint(bot),
        GetMuteEndpoint(bot),
        GetWarnEndpoint(bot),
        UpdateBanEndpoint(bot),
        UpdateSoftbanEndpoint(bot),
        UpdateHardbanEndpoint(bot),
        UpdateKickEndpoint(bot),
        UpdateMuteEndpoint(bot),
        UpdateWarnEndpoint(bot)
    )

    init {
        val router = Router.router(vertx)
        router.route().handler(CorsHandler.create("*").allowedHeaders(setOf("token", "content-type")).allowedMethods(endpoints.asSequence().map { it.method }.toSet()))
        router.route().handler(BodyHandler.create())
        for (endpoint in endpoints) {
            router.route(endpoint.method, endpoint.route).handler(endpoint)
        }
        val server = vertx.createHttpServer()
        server.requestHandler(router).listen(bot.config.server.port)
        LoggerFactory.getLogger(this::class.java).info("Server ready.")
    }
}
