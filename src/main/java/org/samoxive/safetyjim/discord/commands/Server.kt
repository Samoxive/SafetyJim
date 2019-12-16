package org.samoxive.safetyjim.discord.commands

import java.awt.Color
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.tryhardAsync

class Server : Command() {
    override val usages = arrayOf("server - displays information about the current server")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val guild = event.guild
        val owner = tryhardAsync { guild.retrieveOwner().await() }?.user
        val channel = event.channel
        val message = event.message
        val creationDate = guild.timeCreated.toLocalDate().toString()

        val embed = EmbedBuilder()
        embed.setAuthor(guild.name, null, guild.iconUrl)
        embed.setColor(Color(0x4286F4))
        embed.addField("Server Owner", owner?.getTag(), true)
        embed.addField("Creation Date", creationDate, true)

        message.successReact()
        channel.trySendMessage(embed.build())

        return false
    }
}
