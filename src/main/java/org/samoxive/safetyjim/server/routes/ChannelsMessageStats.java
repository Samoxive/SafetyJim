package org.samoxive.safetyjim.server.routes;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.helpers.Pair;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;
import org.samoxive.safetyjim.server.ServerUtils;
import org.samoxive.safetyjim.server.entities.Stat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChannelsMessageStats extends RequestHandler {
    public ChannelsMessageStats(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    @Override
    public void handle(RoutingContext ctx, HttpServerRequest request, HttpServerResponse response) {
        Member member = ServerUtils.getMember(bot, request, response, database, config);
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

        int interval = Stat.getPreferredInterval(from, to);
        Map<String, List<Stat>> stats = guild.getTextChannels()
                .stream()
                .filter((channel) -> member.hasPermission(channel, Permission.MESSAGE_READ))
                .collect(Collectors.toMap((channel) -> channel.getId(), (channel) -> Stat.getChannelMessageStats(database, guild.getId(), channel.getId(), from, to, interval)));

        if (stats.isEmpty()) {
            response.setStatusCode(403);
            response.end();
            return;
        }

        response.putHeader("Content-Type", "application/json");
        response.end(ServerUtils.gson.toJson(stats, stats.getClass()));
    }
}
