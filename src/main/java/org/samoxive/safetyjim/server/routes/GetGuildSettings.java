package org.samoxive.safetyjim.server.routes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;
import org.samoxive.safetyjim.server.entities.GuildSettings;
import org.samoxive.safetyjim.server.entities.PartialChannel;
import org.samoxive.safetyjim.server.entities.PartialRole;

import java.util.List;
import java.util.stream.Collectors;

public class GetGuildSettings extends RequestHandler {
    public GetGuildSettings(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    @Override
    public void handle(RoutingContext ctx, Server server, DiscordBot bot, DSLContext database) {
        HttpServerRequest request = ctx.request();
        HttpServerResponse response = ctx.response();

        String guildId = request.getParam("guildId");
        int shardId = DiscordUtils.getShardIdFromGuildId(Long.parseLong(guildId), bot.getConfig().jim.shard_count);
        JDA shard = bot.getShards().get(shardId).getShard();
        Guild guild = shard.getGuildById(guildId);
        SettingsRecord record = database.selectFrom(Tables.SETTINGS)
                                        .where(Tables.SETTINGS.GUILDID.eq(guildId))
                                        .fetchAny();

        if (record == null || guild == null) {
            response.setStatusCode(404);
            response.end();
            return;
        }

        Gson gson = new GsonBuilder().serializeNulls().create();
        List<PartialChannel> channels = guild.getTextChannels()
                                             .stream()
                                             .map((channel) -> new PartialChannel(channel.getId(), channel.getName()))
                                             .collect(Collectors.toList());
        List<PartialRole> roles = guild.getRoles()
                                       .stream()
                                       .map((role) -> new PartialRole(role.getId(), role.getName()))
                                       .collect(Collectors.toList());

        Channel modLogChannel = shard.getTextChannelById(record.getModlogchannelid());
        PartialChannel modLogChannelPartial = new PartialChannel(modLogChannel.getId(), modLogChannel.getName());
        Channel welcomeMessageChannel = shard.getTextChannelById(record.getWelcomemessagechannelid());
        PartialChannel welcomeMessageChannelPartial = new PartialChannel(welcomeMessageChannel.getId(), welcomeMessageChannel.getName());
        Role holdingRoomRole = null;
        PartialRole holdingRoomRolePartial = null;

        if (record.getHoldingroomroleid() != null) {
            holdingRoomRole = shard.getRoleById(record.getHoldingroomroleid());
            holdingRoomRolePartial = new PartialRole(holdingRoomRole.getId(), holdingRoomRole.getName());
        }

        GuildSettings settings = new GuildSettings(
                guildId,
                record.getModlog(),
                modLogChannelPartial,
                record.getHoldingroom(),
                holdingRoomRolePartial,
                record.getHoldingroomminutes(),
                record.getInvitelinkremover(),
                record.getWelcomemessage(),
                record.getMessage(),
                welcomeMessageChannelPartial,
                record.getPrefix(),
                record.getSilentcommands(),
                record.getNospaceprefix(),
                record.getStatistics(),
                channels,
                roles
        );
        String responseJson = gson.toJson(settings);
        response.putHeader("Content-Type", "application/json");
        response.end(responseJson);
    }
}
