package org.samoxive.safetyjim.server.routes;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
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

public class ChannelMessageStats extends RequestHandler {
    public ChannelMessageStats(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    @Override
    public void handle(RoutingContext ctx, Server server, DiscordBot bot, DSLContext database) {
        HttpServerRequest request = ctx.request();
        HttpServerResponse response = ctx.response();

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
        String channelId = request.getParam("channelId");
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            response.setStatusCode(404);
            response.end();
            return;
        }

        if (!member.hasPermission(channel, Permission.MESSAGE_READ)) {
            response.setStatusCode(403);
            response.end();
            return;
        }

        SettingsRecord settings = DatabaseUtils.getGuildSettings(database, guild);
        if (!settings.getStatistics()) {
            response.setStatusCode(418);
            response.end();
            return;
        }

        int interval = Stat.getPreferredInterval(from, to);
        List<Stat> stats = Stat.getChannelMessageStats(database, guild.getId(), channelId, from, to, interval);
        response.putHeader("Content-Type", "application/json");
        response.end(ServerUtils.gson.toJson(stats, stats.getClass()));
    }
}
