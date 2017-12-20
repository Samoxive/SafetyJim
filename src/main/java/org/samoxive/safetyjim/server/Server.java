package org.samoxive.safetyjim.server;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.jooq.DSLContext;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.server.routes.Guilds;
import org.samoxive.safetyjim.server.routes.Login;
import org.samoxive.safetyjim.server.routes.Self;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server {
    private Logger log = LoggerFactory.getLogger(Server.class);
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

        router.get("/login").handler(new Login(bot, database, this, config));
        router.get("/guilds").handler(new Guilds(bot, database, this, config));
        router.get("/self").handler(new Self(bot, database, this, config));
        router.options().handler((ctx) -> {
            HttpServerResponse response = ctx.response();
            response.putHeader("Access-Control-Allow-Origin", config.server.base_url);
            response.putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
            response.putHeader("Access-Control-Allow-Headers", "token");
            response.end();
        });

        vertx.createHttpServer()
             .requestHandler((request) -> {
                 request.response().putHeader("Access-Control-Allow-Origin", config.server.base_url);
                 router.accept(request);
             })
             .listen(config.server.port, "0.0.0.0");
        log.info("Started web server.");

    }
}
