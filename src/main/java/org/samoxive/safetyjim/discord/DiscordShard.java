package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.SessionReconnectQueue;

import javax.security.auth.login.LoginException;

public class DiscordShard extends ListenerAdapter {
    private DiscordBot bot;
    private JDA shard;
    private int shardId;

    public DiscordShard(DiscordBot bot, int shardId) {
        this.bot = bot;
        this.shardId = shardId;
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
        System.out.println(event.getJDA().getShardInfo() + " ready.");
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getMessage().getContent().equals("ping")) {
            event.getChannel().sendMessage("Pong").complete();
        }
    }
}
