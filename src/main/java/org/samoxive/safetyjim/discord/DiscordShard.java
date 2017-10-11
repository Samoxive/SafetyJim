package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;

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

        for (Guild guild: event.getJDA().getGuilds()) {
            if (DiscordUtils.isBotFarm(guild)) {
                guild.leave().queue();
            }
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
        super.onGuildMessageDelete(event);
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        super.onGuildMessageReactionAdd(event);
    }

    @Override
    public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
        super.onGuildMessageReactionRemove(event);
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        super.onGuildJoin(event);
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
