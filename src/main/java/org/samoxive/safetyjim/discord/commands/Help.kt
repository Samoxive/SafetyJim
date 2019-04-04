package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.successReact
import org.samoxive.safetyjim.discord.trySendMessage

class Help : Command() {
    override val usages = arrayOf("help - lists all the available commands and their usage")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        event.message.successReact(bot)
        event.channel.trySendMessage("This command has been deprecated! Visit https://safetyjim.xyz/commands for more information.")
        return false
    }
}
