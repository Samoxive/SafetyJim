package org.samoxive.safetyjim.server.routes;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.OauthsecretsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordApiUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;
import org.samoxive.safetyjim.server.ServerUtils;
import org.samoxive.safetyjim.server.entities.GuildEntity;
import org.samoxive.safetyjim.server.entities.PartialChannel;

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
    public void handle(RoutingContext ctx, HttpServerRequest request, HttpServerResponse response) {
        String userId = ServerUtils.authUser(request, response, database, config);
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
        List<GuildEntity> result = jimGuilds.stream()
                                            .filter((guild) -> isInUserGuilds(guild, userGuilds))
                                            .map((guild) -> {
                                                String url = guild.getIconUrl();
                                                Member member = guild.getMemberById(userId);
                                                List<PartialChannel> channels = guild.getTextChannels()
                                                                                     .stream()
                                                                                     .filter((channel) -> member.hasPermission(channel, Permission.MESSAGE_READ))
                                                                                     .map((channel) -> new PartialChannel(channel.getId(), channel.getName()))
                                                                                     .collect(Collectors.toList());
                                                return new GuildEntity(guild.getId(), guild.getName(), url, channels);
                                            })
                                            .collect(Collectors.toList());

        response.putHeader("Content-Type", "application/json");
        response.end(ServerUtils.gson.toJson(result));
    }
}
