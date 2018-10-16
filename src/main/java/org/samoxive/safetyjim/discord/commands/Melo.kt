package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.discord.*

class Melo : Command() {
    override val usages = arrayOf("melo - \uD83C\uDF48")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        event.message.meloReact()
        return false
    }
}
