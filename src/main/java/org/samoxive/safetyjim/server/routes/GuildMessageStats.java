package org.samoxive.safetyjim.server.routes;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
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

public class GuildMessageStats extends RequestHandler {
    public GuildMessageStats(DiscordBot bot, DSLContext database, Server server, Config config) {
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
        List<Stat> stats = Stat.getGuildMessageStats(database, guild.getId(), from, to, interval);
        response.putHeader("Content-Type", "application/json");
        response.end(ServerUtils.gson.toJson(stats, stats.getClass()));
    }
}
