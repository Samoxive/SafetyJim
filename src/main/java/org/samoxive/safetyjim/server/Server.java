package org.samoxive.safetyjim.server;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import org.jooq.DSLContext;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.server.routes.Login;

public class Server {
    private DiscordBot bot;
    private DSLContext database;
    private Config config;
    private Vertx vertx;

    public Server(DiscordBot bot, DSLContext database, Config config) {
        this.bot = bot;
        this.database = database;
        this.config = config;

        vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.route("/login").handler(new Login(bot, database, this, config));

        vertx.createHttpServer()
             .requestHandler(router::accept)
             .listen(8080, "0.0.0.0");
    }
}
