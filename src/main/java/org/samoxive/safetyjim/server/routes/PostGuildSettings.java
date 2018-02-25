package org.samoxive.safetyjim.server.routes;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordShard;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.commands.Settings;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;
import org.samoxive.safetyjim.server.ServerUtils;
import org.samoxive.safetyjim.server.entities.GuildSettings;

import java.util.Optional;

public class PostGuildSettings extends RequestHandler {
    public PostGuildSettings(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
    }

    @Override
    public void handle(RoutingContext ctx, HttpServerRequest request, HttpServerResponse response) {
        Member member = ServerUtils.getMember(bot, request, response, database, config);
        if (member == null) {
            return;
        }

        String body = ctx.getBodyAsString();
        GuildSettings newSettings = ServerUtils.gson.fromJson(body, GuildSettings.class);

        if (!isSettingsValid(newSettings)) {
            response.setStatusCode(400);
            response.end();
            return;
        }

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
           response.setStatusCode(403);
           response.end();
           return;
        }

        Guild guild = member.getGuild();
        Optional<TextChannel> welcomeChannel = guild.getTextChannels().stream()
                .filter((channel) -> channel.getId().equals(newSettings.welcomeMessageChannel.id))
                .findAny();

        Optional<TextChannel> modlogChannel = guild.getTextChannels().stream()
                .filter((channel) -> channel.getId().equals(newSettings.modLogChannel.id))
                .findAny();

        if (!modlogChannel.isPresent() || !welcomeChannel.isPresent()) {
            response.setStatusCode(400);
            response.end();
            return;
        }

        if (newSettings.holdingRoomRole != null) {
            Optional<Role> holdingRoomRole = guild.getRoles().stream()
                    .filter((role) -> role.getId().equals(newSettings.holdingRoomRole.id))
                    .findAny();

            if (!holdingRoomRole.isPresent()) {
                response.setStatusCode(400);
                response.end();
                return;
            }
        } else {
            if (newSettings.holdingRoom) {
                response.setStatusCode(400);
                response.end();
                return;
            }
        }

        SettingsRecord record = database.selectFrom(Tables.SETTINGS)
                                        .where(Tables.SETTINGS.GUILDID.eq(guild.getId()))
                                        .fetchAny();

        record.setModlog(newSettings.modLog);
        record.setModlogchannelid(newSettings.modLogChannel.id);
        record.setHoldingroom(newSettings.holdingRoom);
        record.setHoldingroomroleid(newSettings.holdingRoomRole == null ? null : newSettings.holdingRoomRole.id);
        record.setHoldingroomminutes(newSettings.holdingRoomMinutes);
        record.setInvitelinkremover(newSettings.inviteLinkRemover);
        record.setWelcomemessage(newSettings.welcomeMessage);
        record.setMessage(newSettings.message);
        record.setWelcomemessagechannelid(newSettings.welcomeMessageChannel.id);
        record.setPrefix(newSettings.prefix);
        record.setSilentcommands(newSettings.silentCommands);
        record.setNospaceprefix(newSettings.noSpacePrefix);
        record.setStatistics(newSettings.statistics);
        record.update();

        if (newSettings.statistics) {
            DiscordShard shard = bot.getShards().get(DiscordUtils.getShardIdFromGuildId(guild.getIdLong(), config.jim.shard_count));
            shard.getThreadPool().submit(() -> shard.populateGuildStatistics(guild));
            Settings.kickstartStatistics(database, guild);
        }

        response.setStatusCode(200);
        response.end();
    }

    private boolean isSettingsValid(GuildSettings guildSettings) {
        return guildSettings.id != null &&
                guildSettings.modLog != null &&
                guildSettings.holdingRoom != null &&
                guildSettings.holdingRoomMinutes != null &&
                guildSettings.inviteLinkRemover != null &&
                guildSettings.welcomeMessage != null &&
                guildSettings.message != null &&
                guildSettings.prefix != null &&
                guildSettings.silentCommands != null &&
                guildSettings.noSpacePrefix != null &&
                guildSettings.statistics != null &&
                guildSettings.modLogChannel != null &&
                guildSettings.welcomeMessageChannel != null &&
                guildSettings.modLogChannel.id != null &&
                guildSettings.welcomeMessageChannel.id != null && (guildSettings.holdingRoomRole == null || guildSettings.holdingRoomRole.id != null);

    }
}
