package org.samoxive.safetyjim.server

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.endpoints.*

class Server(val bot: DiscordBot) {
    val vertx: Vertx = Vertx.vertx()
    val endpoints = listOf(
            LoginEndpoint(bot),
            TestEndpoint(bot),
            SelfUserEndpoint(bot),
            GetGuildSettingsEndpoint(bot),
            PostGuildSettingsEndpoint(bot),
            ResetGuildSettingsEndpoint(bot)
    )

    init {
        val router = Router.router(vertx)
        router.route().handler(CorsHandler.create("*").allowedHeaders(setOf("token", "content-type")).allowedMethods(endpoints.map { it.method }.toSet()))
        router.route().handler(BodyHandler.create())
        for (endpoint in endpoints) {
            router.route(endpoint.method, endpoint.route).handler(endpoint)
        }
        val server = vertx.createHttpServer()
        server.requestHandler { router.accept(it) }.listen(8080)
    }
}