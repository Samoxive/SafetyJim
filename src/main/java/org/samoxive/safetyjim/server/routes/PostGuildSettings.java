package org.samoxive.safetyjim.server.routes;

import com.google.gson.Gson;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
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

import java.util.Optional;

public class PostGuildSettings extends RequestHandler {
    public PostGuildSettings(DiscordBot bot, DSLContext database, Server server, Config config) {
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

        OauthsecretsRecord oauthRecord = database.selectFrom(Tables.OAUTHSECRETS)
                .where(Tables.OAUTHSECRETS.USERID.eq(userId))
                .fetchAny();

        if (oauthRecord == null) {
            response.setStatusCode(401);
            response.end();
            return;
        }

        String body = ctx.getBodyAsString();
        Gson gson = new Gson();
        GuildSettings newSettings = gson.fromJson(body, GuildSettings.class);

        if (!isSettingsValid(newSettings)) {
            response.setStatusCode(400);
            response.end();
            return;
        }

        String guildId = request.getParam("guildId");
        int shardId = DiscordUtils.getShardIdFromGuildId(Long.parseLong(guildId), config.jim.shard_count);
        Guild guild = bot.getShards().get(shardId).getShard().getGuildById(guildId);

        if (guild == null) {
            response.setStatusCode(400);
            response.end();
            return;
        }

        Member member = guild.getMemberById(userId);
        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
           response.setStatusCode(403);
           response.end();
           return;
        }

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
