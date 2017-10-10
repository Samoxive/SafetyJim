package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.jooq.DSLContext;

public class DiscordShard extends ListenerAdapter {
    private DiscordBot bot;
    private DSLContext database;

    public DiscordShard(DiscordBot bot, DSLContext database) {
        this.bot = bot;
        this.database = database;
    }
}
