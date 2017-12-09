package org.samoxive.safetyjim.server.routes;

import com.google.gson.Gson;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.entities.Guild;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.OauthsecretsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordApiUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.entities.PartialGuild;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;
import org.samoxive.safetyjim.server.ServerUtils;
import org.samoxive.safetyjim.server.entries.GuildEntity;

import java.util.List;
import java.util.stream.Collectors;

public class Guilds extends RequestHandler {
    public Guilds(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    @Override
    public void handle(RoutingContext ctx, Server server, DiscordBot bot, DSLContext database) {
        HttpServerRequest request = ctx.request();
        HttpServerResponse response = ctx.response();

        String token = request.getHeader("token");
        if (token == null) {
            response.setStatusCode(403);
            response.end();
            return;
        }

        String userId = ServerUtils.getIdFromToken(config, token);

        if (userId == null) {
            response.setStatusCode(403);
            response.end();
            return;
        }

        OauthsecretsRecord record = database.selectFrom(Tables.OAUTHSECRETS)
                                            .where(Tables.OAUTHSECRETS.USERID.eq(userId))
                                            .fetchAny();

        if (record == null) {
            response.setStatusCode(403);
            response.end();
            return;
        }


        List<String> jimGuilds = bot.getGuilds()
                                   .stream()
                                   .map((guild -> guild.getId()))
                                   .collect(Collectors.toList());
        List<PartialGuild> partialGuilds = DiscordApiUtils.getUserGuilds(record.getAccesstoken());
        List<GuildEntity> result = partialGuilds.stream()
                                                .filter((guild) -> jimGuilds.contains(guild.id))
                                                .map((guild) -> {
                                                    String url = DiscordApiUtils.getGuildIconUrl(guild.id, guild.icon);
                                                    return new GuildEntity(guild.id, guild.name, url);
                                                })
                                                .collect(Collectors.toList());

        Gson gson = new Gson();
        response.putHeader("Content-Type", "application/json");
        response.end(gson.toJson(result));
    }
}
