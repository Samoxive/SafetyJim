package org.samoxive.safetyjim.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.Oauthsecrets;
import org.samoxive.jooq.generated.tables.records.OauthsecretsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordShard;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.helpers.Pair;

import java.net.URLEncoder;

public class ServerUtils {
    public static Gson gson = new Gson();

    public static String authUser(HttpServerRequest request, HttpServerResponse response, DSLContext database, Config config) {
        String token = request.getHeader("token");
        if (token == null) {
            response.setStatusCode(403);
            response.end();
            return null;
        }

        String userId = ServerUtils.getIdFromToken(config, token);

        if (userId == null) {
            response.setStatusCode(401);
            response.end();
            return null;
        }

        OauthsecretsRecord record = database.selectFrom(Tables.OAUTHSECRETS)
                                            .where(Tables.OAUTHSECRETS.USERID.eq(userId))
                                            .fetchAny();

        if (record == null) {
            response.setStatusCode(403);
            response.end();
            return null;
        }

        return userId;
    }

    public static String getIdFromToken(Config config, String token) {
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

    public static Member getMember(DiscordBot bot, HttpServerRequest request, HttpServerResponse response, DSLContext database, Config config) {
        String userId = authUser(request, response, database, config);
        if (userId == null) {
            response.setStatusCode(401);
            return null;
        }

        String guildId = request.getParam("guildId");
        long guildIdLong;
        try {
            guildIdLong = Long.parseLong(guildId);
        } catch (Exception e) {
            response.setStatusCode(400);
            response.end();
            return null;
        }

        int shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config.jim.shard_count);
        JDA shard = bot.getShards()
                       .get(shardId)
                       .getShard();

        Guild guild = shard.getGuildById(guildIdLong);
        if (guild == null) {
            response.setStatusCode(404);
            response.end();
            return null;
        }

        Member member = guild.getMemberById(userId);
        if (member == null) {
            response.setStatusCode(404);
            response.end();
            return null;
        }

        return member;
    }

    public static Pair<Long, Long> validateFromAndTo(HttpServerRequest request, HttpServerResponse response) {
        String fromParam = request.getParam("from");
        String toParam = request.getParam("to");

        if (fromParam == null || toParam == null) {
            response.setStatusCode(400);
            response.end();
            return null;
        }

        try {
            long from = Long.parseLong(fromParam);
            long to = Long.parseLong(toParam);

            if (from <= 0 || to <= 0 || from >= to) {
                throw new Exception();
            } else {
                return new Pair<>(from, to);
            }
        } catch (Exception e) {
            response.setStatusCode(400);
            response.end();
            return null;
        }
    }

    public static void redirectToDiscord(HttpServerResponse response, Config config) {
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

    public static void redirectToWebsite(HttpServerResponse response) {
        response.setStatusCode(302);
        response.putHeader("Location", "http://localhost.com");
        response.end();
    }
}
