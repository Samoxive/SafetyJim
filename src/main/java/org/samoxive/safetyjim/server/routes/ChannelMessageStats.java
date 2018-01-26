package org.samoxive.safetyjim.server.routes;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import org.samoxive.safetyjim.discord.DiscordUtils;
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

        String userId = ServerUtils.authUser(request, response, config);
        if (userId == null) {
            return;
        }

        String guildId = request.getParam("guildId");
        String channelId = request.getParam("channelId");
        String fromParam = request.getParam("from");
        String toParam = request.getParam("to");

        long from;
        long to;

        try {
            from = Long.parseLong(fromParam);
            to = Long.parseLong(toParam);

            if (from <= 0 || to <= 0 || from >= to) {
                response.setStatusCode(400);
                response.end();
                return;
            }
        } catch (NumberFormatException e) {
            response.setStatusCode(400);
            response.end();
            return;
        }

        Guild guild = DiscordUtils.getGuildFromBot(bot, guildId);
        if (guild == null) {
            response.setStatusCode(404);
            response.end();
            return;
        }

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            response.setStatusCode(403);
            response.end();
            return;
        }

        Member member = guild.getMemberById(userId);
        if (member == null) {
            response.setStatusCode(403);
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
        Gson gson  = new Gson();
        List<Stat> stats = Stat.getChannelMessageStats(database, guildId, channelId, from, to, interval);
        response.putHeader("Content-Type", "application/json");
        response.end(gson.toJson(stats, stats.getClass()));
    }
}
