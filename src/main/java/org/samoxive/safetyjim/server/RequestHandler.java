package org.samoxive.safetyjim.server;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.jooq.DSLContext;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;

public abstract class RequestHandler implements Handler<RoutingContext> {
    protected DiscordBot bot;
    protected DSLContext database;
    protected Server server;
    protected Config config;

    public RequestHandler(DiscordBot bot, DSLContext database, Server server, Config config) {
        this.bot = bot;
        this.database = database;
        this.server = server;
        this.config = config;
    }

    public void handle(RoutingContext ctx) {
        handle(ctx, ctx.request(), ctx.response());
    }

    public abstract void handle(RoutingContext ctx, HttpServerRequest request, HttpServerResponse response);
}
