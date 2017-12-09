package org.samoxive.safetyjim.server.routes;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.OauthsecretsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordApiUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.entities.DiscordSecrets;
import org.samoxive.safetyjim.discord.entities.SelfUser;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;

import java.util.Date;

public class Login extends RequestHandler {
    public Login(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }



    private String getJwtToken(String userId) {
        try {
            Algorithm algorithm = Algorithm.HMAC512(config.server.secret);
            return JWT.create()
                      .withClaim("userId", userId)
                      .sign(algorithm);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void handle(RoutingContext ctx, Server server, DiscordBot bot, DSLContext database) {
        HttpServerRequest request = ctx.request();
        HttpServerResponse response = ctx.response();
        String code = request.getParam("code");

        if (code == null) {
            response.setStatusCode(400);
            response.end();
            return;
        }

        DiscordSecrets secrets = DiscordApiUtils.getUserSecrets(config, code);
        if (secrets == null || !secrets.scope.equals("guilds identify")) {
            response.setStatusCode(400);
            response.end();
            return;
        }

        SelfUser self = DiscordApiUtils.getSelf(secrets.accessToken);
        if (self == null) {
            response.setStatusCode(400);
            response.end();
            return;
        }

        Long now = (new Date().getTime()) / 1000;
        OauthsecretsRecord record = database.newRecord(Tables.OAUTHSECRETS);
        record.setUserid(self.id);
        record.setAccesstoken(secrets.accessToken);
        record.setRefreshtoken(secrets.refreshToken);
        record.setExpirationdate(now + secrets.expiresIn);

        database.insertInto(Tables.OAUTHSECRETS)
                .set(record)
                .onDuplicateKeyUpdate()
                .set(record)
                .execute();

        response.putHeader("Access-Control-Allow-Origin", config.server.base_url);
        String token = getJwtToken(self.id);
        response.putHeader("Content-Type", "application/json");
        response.end("\"" + token + "\"");
    }
}
