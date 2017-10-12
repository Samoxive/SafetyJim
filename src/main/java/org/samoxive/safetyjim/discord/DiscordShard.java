package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.*;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.SessionReconnectQueue;
import org.jooq.DSLContext;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.util.Map;

public class DiscordShard extends ListenerAdapter {
    private Logger log;
    private DiscordBot bot;
    private JDA shard;
    private int shardId;

    public DiscordShard(DiscordBot bot, int shardId) {
        this.bot = bot;
        this.shardId = shardId;
        log = LoggerFactory.getLogger("DiscordShard " + DiscordUtils.getShardString(shardId, bot.getConfig().jim.shard_count));

        JDABuilder builder = new JDABuilder(AccountType.BOT);
        try {
            this.shard = builder.setToken(bot.getConfig().jim.token)
                                .setAudioEnabled(false)
                                .addEventListener(this)
                                .setReconnectQueue(new SessionReconnectQueue())
                                .setEnableShutdownHook(true)
                                .useSharding(shardId, bot.getConfig().jim.shard_count)
                                .buildBlocking();
        } catch (LoginException e) {
            System.out.println("Invalid token.");
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println("Something something");
        } catch (RateLimitedException e) {
            System.out.println("Hit Discord API Rate Limit");
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        log.info("Shard is ready.");
        // TODO(sam): Change the game text
        event.getJDA().getPresence().setGame(Game.of("-mod help"));
        DSLContext database = this.bot.getDatabase();
        for (Guild guild: event.getJDA().getGuilds()) {
            if (DiscordUtils.isBotFarm(guild)) {
                guild.leave().queue();
            }
        }

        int guildsWithMissingKeys = 0;
        for (Guild guild: event.getJDA().getGuilds()) {
            Map<String, String> guildSettings = DatabaseUtils.getGuildSettings(database, guild);

            if (guildSettings.size() != DatabaseUtils.possibleSettingKeys.length) {
                DatabaseUtils.deleteGuildSettings(database, guild);
                DatabaseUtils.createGuildSettings(this.bot, database, guild);
                guildsWithMissingKeys++;
            }
        }

        if (guildsWithMissingKeys > 0) {
            log.warn("Added {} guild(s) to the database with invalid number of settings.", guildsWithMissingKeys);
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getMessage().getContent().equals("ping")) {
            event.getChannel().sendMessage("Pong, Guild Count" + bot.getGuildCount()).complete();
        }
    }

    @Override
    public void onResume(ResumedEvent event) {
        super.onResume(event);
    }

    @Override
    public void onReconnect(ReconnectedEvent event) {
        super.onReconnect(event);
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
        super.onDisconnect(event);
    }

    @Override
    public void onException(ExceptionEvent event) {
        super.onException(event);
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        // TODO(sam): Add message cache and trigger message processors if
        // deleted message is in the cache
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (event.getMember().getUser().isBot() || event.getChannelType() != ChannelType.TEXT) {
            return;
        }

        for (MessageProcessor processor: bot.getProcessors().values()) {
            processor.onReactionAdd(bot, event);
        }
    }

    @Override
    public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
        if (event.getMember().getUser().isBot() || event.getChannelType() != ChannelType.TEXT) {
            return;
        }

        for (MessageProcessor processor: bot.getProcessors().values()) {
            processor.onReactionRemove(bot, event);
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        if (DiscordUtils.isBotFarm(event.getGuild())) {
            event.getGuild().leave().queue();
        }

        String message = String.format("Hello! I am Safety Jim, `%s` is my default prefix!", bot.getConfig().jim.default_prefix);
        DiscordUtils.sendMessage(DiscordUtils.getDefaultChannel(event.getGuild()), message);
        DatabaseUtils.createGuildSettings(bot, bot.getDatabase(), event.getGuild());
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        super.onGuildLeave(event);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        super.onGuildMemberJoin(event);
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        super.onGuildMemberLeave(event);
    }

    public JDA getShard() {
        return shard;
    }
}
