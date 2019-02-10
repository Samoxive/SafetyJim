package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.JimSettings
import org.samoxive.safetyjim.discord.*

class Help : Command() {
    override val usages = arrayOf("help - lists all the available commands and their usage")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: JimSettings, args: String): Boolean {
        event.message.successReact(bot)
        event.channel.trySendMessage("This command has been deprecated! Visit https://safetyjim.xyz/commands for more information.")
        return false
    }
}
