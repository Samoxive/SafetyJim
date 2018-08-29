package org.samoxive.safetyjim.discord

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent

abstract class Command {
    abstract val usages: Array<String>
    abstract fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean
}
