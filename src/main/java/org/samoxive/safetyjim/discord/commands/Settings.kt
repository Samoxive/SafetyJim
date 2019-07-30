package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.SettingsTable
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

class Settings : Command() {
    override val usages = arrayOf<String>()

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val message = event.message
        val channel = event.channel
        val guild = event.guild

        channel.trySendMessage("This command has been deprecated! Visit https://safetyjim.xyz/dashboard/${guild.id}/settings to change settings.")

        message.successReact()
        return false
    }
}
