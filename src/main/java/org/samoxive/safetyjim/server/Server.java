package org.samoxive.safetyjim.server;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.jooq.DSLContext;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.server.routes.Login;
import org.samoxive.safetyjim.server.routes.Root;
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

        router.route("/").handler(new Root(bot, database, this, config));
        router.route("/login").handler(new Login(bot, database, this, config));


        vertx.createHttpServer()
             .requestHandler(router::accept)
             .listen(config.server.port, "0.0.0.0");
        log.info("Started web server.");

    }
}
