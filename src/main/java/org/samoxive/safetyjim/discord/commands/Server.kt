package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils

import java.awt.*

class Server : Command() {
    override val usages = arrayOf("server - displays information about the current server")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val guild = event.guild
        val owner = guild.owner.user
        val channel = event.channel
        val message = event.message
        val memberCount = java.lang.Long.toString(guild.memberCache.size())
        val creationDate = guild.creationTime.toLocalDate().toString()
        val emojis = StringBuilder()

        for (emote in guild.emotes) {
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
        embed.addField("Server Owner", DiscordUtils.getTag(owner), true)
        embed.addField("Member Count", memberCount, true)
        embed.addField("Creation Date", creationDate, true)
        embed.addField("Emojis", emojiString, false)

        DiscordUtils.successReact(bot, message)
        DiscordUtils.sendMessage(channel, embed.build())

        return false
    }
}
