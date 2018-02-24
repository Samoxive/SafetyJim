package org.samoxive.safetyjim.server.routes;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.helpers.Pair;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;
import org.samoxive.safetyjim.server.ServerUtils;

import java.util.Map;
import java.util.stream.Collectors;

public class StatsOverview extends RequestHandler {
    public StatsOverview(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    @Override
    public void handle(RoutingContext ctx, HttpServerRequest request, HttpServerResponse response) {
        Member member = ServerUtils.getMember(bot, request, response, config);
        if (member == null) {
            return;
        }

        Pair<Long, Long> fromToPair = ServerUtils.validateFromAndTo(request, response);
        if (fromToPair == null) {
            return;
        }

        long from = fromToPair.getLeft();
        long to = fromToPair.getRight();

        Guild guild = member.getGuild();

        SettingsRecord settings = DatabaseUtils.getGuildSettings(database, guild);
        if (!settings.getStatistics()) {
            response.setStatusCode(418);
            response.end();
            return;
        }

        Map<String, Integer> channelStats = guild.getTextChannels()
                                          .stream()
                                          .filter((channel) -> member.hasPermission(channel, Permission.MESSAGE_READ))
                                          .collect(Collectors.toMap((channel) -> channel.getName(), (channel) ->
                                              database.fetchCount(
                                                      database.selectFrom(Tables.MESSAGES)
                                                              .where(Tables.MESSAGES.GUILDID.eq(guild.getId()))
                                                              .and(Tables.MESSAGES.CHANNELID.eq(channel.getId()))
                                                              .and(Tables.MESSAGES.DATE.between(from, to))
                                              )
                                          ));

        JSONObject result = new JSONObject();
        result.put("delta", to - from);
        result.put("channelStats", channelStats);
        response.putHeader("Content-Type", "application/json");
        response.end(result.toString());
    }
}
