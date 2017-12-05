package org.samoxive.safetyjim.server;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class Server {
    public Server() {
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        router.route().handler((ctx) -> ctx.response().end("ayylmao"));
        router.route("/:x/:y").handler((RoutingContext context) -> {
            HttpServerRequest request = context.request();
            int x = Integer.parseInt(request.getParam("x"));
            int y = Integer.parseInt(request.getParam("y"));
            context.response().end(x + y + "");
        });

        vertx.createHttpServer()
             .requestHandler(router::accept)
             .listen(8080, "0.0.0.0");
    }
}
