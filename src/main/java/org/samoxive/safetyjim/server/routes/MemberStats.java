package org.samoxive.safetyjim.server.routes;

import com.google.gson.Gson;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.MembercountsRecord;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.helpers.Pair;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;
import org.samoxive.safetyjim.server.ServerUtils;
import org.samoxive.safetyjim.server.entities.MemberStatsResponse;
import org.samoxive.safetyjim.server.entities.Stat;

import java.util.ArrayList;
import java.util.List;

public class MemberStats extends RequestHandler {
    public MemberStats(DiscordBot bot, DSLContext database, Server server, Config config) {
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

        Result<MembercountsRecord> records = database.selectFrom(Tables.MEMBERCOUNTS)
                                                     .where(Tables.MEMBERCOUNTS.GUILDID.eq(guild.getId()))
                                                     .and(Tables.MEMBERCOUNTS.DATE.between(from, to))
                                                     .fetch();

        List<Stat> onlineStats = new ArrayList<>();
        List<Stat> totalStats = new ArrayList<>();
        for (MembercountsRecord record: records) {
            int date = (int) (record.getDate() / 1000);
            onlineStats.add(new Stat(date, record.getOnlinecount()));
            totalStats.add(new Stat(date, record.getCount()));
        }

        Gson gson = new Gson();
        response.putHeader("Content-Type", "application/json");
        response.end(gson.toJson(new MemberStatsResponse(onlineStats, totalStats)));
    }
}
