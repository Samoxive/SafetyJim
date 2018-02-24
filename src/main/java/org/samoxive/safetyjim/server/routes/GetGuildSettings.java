package org.samoxive.safetyjim.server.routes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.OauthsecretsRecord;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;
import org.samoxive.safetyjim.server.ServerUtils;
import org.samoxive.safetyjim.server.entities.GuildSettings;
import org.samoxive.safetyjim.server.entities.PartialChannel;
import org.samoxive.safetyjim.server.entities.PartialRole;

import java.util.List;
import java.util.stream.Collectors;

public class GetGuildSettings extends RequestHandler {
    private Gson gson = new GsonBuilder().serializeNulls().create();

    public GetGuildSettings(DiscordBot bot, DSLContext database, Server server, Config config) {
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

        Guild guild = member.getGuild();
        SettingsRecord record = database.selectFrom(Tables.SETTINGS)
                                        .where(Tables.SETTINGS.GUILDID.eq(guild.getId()))
                                        .fetchAny();


        List<PartialChannel> channels = guild.getTextChannels()
                                             .stream()
                                             .filter((channel) -> member.hasPermission(channel, Permission.MESSAGE_READ))
                                             .map((channel) -> new PartialChannel(channel.getId(), channel.getName()))
                                             .collect(Collectors.toList());

        List<PartialRole> roles = guild.getRoles()
                                       .stream()
                                       .map((role) -> new PartialRole(role.getId(), role.getName()))
                                       .collect(Collectors.toList());

        Channel modLogChannel = guild.getTextChannelById(record.getModlogchannelid());
        PartialChannel modLogChannelPartial = new PartialChannel(modLogChannel.getId(), modLogChannel.getName());
        Channel welcomeMessageChannel = guild.getTextChannelById(record.getWelcomemessagechannelid());
        PartialChannel welcomeMessageChannelPartial = new PartialChannel(welcomeMessageChannel.getId(), welcomeMessageChannel.getName());
        Role holdingRoomRole = null;
        PartialRole holdingRoomRolePartial = null;

        if (record.getHoldingroomroleid() != null) {
            holdingRoomRole = guild.getRoleById(record.getHoldingroomroleid());
            holdingRoomRolePartial = new PartialRole(holdingRoomRole.getId(), holdingRoomRole.getName());
        }

        GuildSettings settings = new GuildSettings(
                guild.getId(),
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
