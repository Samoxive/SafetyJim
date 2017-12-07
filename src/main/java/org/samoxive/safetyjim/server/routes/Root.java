package org.samoxive.safetyjim.server.routes;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.Oauthsecrets;
import org.samoxive.jooq.generated.tables.records.OauthsecretsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordApiUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;

public class Root extends RequestHandler {
    public Root(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    private String verifyJwtToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC512(config.server.secret);
            JWTVerifier verifier = JWT.require(algorithm)
                                      .build();
            DecodedJWT decodedJWT = verifier.verify(token);
            return decodedJWT.getClaim("userId").asString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void handle(RoutingContext ctx, Server server, DiscordBot bot, DSLContext database) {
        HttpServerRequest request = ctx.request();
        HttpServerResponse response = ctx.response();

        String token = request.getHeader("Cookie").substring(6);
        String userId = verifyJwtToken(token);

        OauthsecretsRecord record = database.selectFrom(Tables.OAUTHSECRETS)
                                            .where(Tables.OAUTHSECRETS.USERID.eq(userId))
                                            .fetchAny();

        String guildsJson = (new Gson()).toJson(DiscordApiUtils.getUserGuilds(record.getAccesstoken()));
        response.end(guildsJson);

    }
}
