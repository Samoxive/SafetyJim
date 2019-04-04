package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import java.awt.Color

class Server : Command() {
    override val usages = arrayOf("server - displays information about the current server")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val guild = event.guild
        val owner = guild.owner.user
        val channel = event.channel
        val message = event.message
        val memberCount = guild.memberCache.size()
        val creationDate = guild.creationTime.toLocalDate().toString()
        val emojis = StringBuilder()

        for (emote in guild.retrieveEmotes().await()) {
            if (emojis.length > 950) {
                emojis.append("...")
                break
            } else {
                emojis.append(emote.asMention)
            }
        }

        var emojiString = emojis.toString()
        emojiString = if (emojiString == "") "None" else emojiString

        val embed = EmbedBuilder()
        embed.setAuthor(guild.name, null, guild.iconUrl)
        embed.setColor(Color(0x4286F4))
        embed.addField("Server Owner", owner.getTag(), true)
        embed.addField("Member Count", memberCount.toString(), true)
        embed.addField("Creation Date", creationDate, true)
        embed.addField("Emojis", emojiString, false)

        message.successReact(bot)
        channel.trySendMessage(embed.build())

        return false
    }
}
