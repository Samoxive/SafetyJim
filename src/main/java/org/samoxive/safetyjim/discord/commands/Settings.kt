package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.successReact
import org.samoxive.safetyjim.discord.trySendMessage

class Settings : Command() {
    override val usages = arrayOf<String>()

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val message = event.message
        val channel = event.channel
        val guild = event.guild

        channel.trySendMessage("This command has been deprecated! Visit https://safetyjim.xyz/dashboard/${guild.id}/settings to change settings.", message)

        message.successReact()
        return false
    }
}
