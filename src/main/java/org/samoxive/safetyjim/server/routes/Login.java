package org.samoxive.safetyjim.server.routes;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Cookie;
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

import java.net.URI;
import java.net.URLEncoder;
import java.util.Date;

public class Login extends RequestHandler {
    public Login(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    private void redirectToDiscord(HttpServerResponse response) {
        try {
            response.setStatusCode(302);
            response.putHeader("Location", "https://discordapp.com/api/oauth2/authorize?" +
                    "client_id=" + config.oauth.client_id +
                    "&redirect_uri=" + URLEncoder.encode(config.oauth.redirect_uri, "utf-8") +
                    "&response_type=code" +
                    "&scope=guilds%20identify");
            response.putHeader("User-Agent", "Safety Jim");
            response.end();
        } catch (Exception e) {

        }
    }

    private void redirectToWebsite(HttpServerResponse response) {
        response.setStatusCode(302);
        response.putHeader("Location", "http://safetyjim.xyz");
        response.end();
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
            redirectToDiscord(response);
            return;
        }

        DiscordSecrets secrets = DiscordApiUtils.getUserSecrets(config, code);
        if (secrets == null || !secrets.scope.equals("guilds identify")) {
            redirectToDiscord(response);
            return;
        }

        SelfUser self = DiscordApiUtils.getSelf(secrets.accessToken);
        if (self == null) {
            redirectToDiscord(response);
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

        Cookie tokenCookie = Cookie.cookie("token", getJwtToken(self.id));
        tokenCookie.setMaxAge(60 * 24 * 1000);
        tokenCookie.setDomain(config.server.base_url);

        response.putHeader("Set-Cookie", tokenCookie.encode());
        response.end(tokenCookie.encode());



    }
}
