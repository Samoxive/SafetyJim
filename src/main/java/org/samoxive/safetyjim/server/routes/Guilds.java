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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Guilds extends RequestHandler {
    public Guilds(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    private static String[] getGuildsOfUser(OauthsecretsRecord record) {
        if (record.getGuilds() != null) {
            return record.getGuilds();
        }

        String[] guildIds = DiscordApiUtils.getUserGuilds(record.getAccesstoken())
                                           .stream()
                                           .map((guild) -> guild.id)
                                           .toArray(String[]::new);
        record.setGuilds(guildIds);
        record.update();
        return guildIds;
    }

    private static boolean isInUserGuilds(Guild guild, String[] userGuilds) {
        for (String userGuild: userGuilds) {
            if (guild.getId().equals(userGuild)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void handle(RoutingContext ctx, Server server, DiscordBot bot, DSLContext database) {
        HttpServerRequest request = ctx.request();
        HttpServerResponse response = ctx.response();

        String userId = ServerUtils.authUser(request, response, config);
        if (userId == null) {
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


        List<Guild> jimGuilds = bot.getGuilds();
        String[] userGuilds = getGuildsOfUser(record);
        long start = System.currentTimeMillis();
        List<GuildEntity> result = jimGuilds.stream()
                                            .filter((guild) -> isInUserGuilds(guild, userGuilds))
                                            .map((guild) -> {
                                                String url = guild.getIconUrl();
                                                return new GuildEntity(guild.getId(), guild.getName(), url);
                                            })
                                            .collect(Collectors.toList());
        long end = System.currentTimeMillis();
        System.out.println(end - start);

        Gson gson = new Gson();
        response.putHeader("Content-Type", "application/json");
        response.end(gson.toJson(result));
    }
}
