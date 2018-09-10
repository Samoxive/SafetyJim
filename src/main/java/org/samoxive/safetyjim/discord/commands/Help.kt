package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.getGuildSettings
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.getUsageString
import org.samoxive.safetyjim.discord.successReact
import java.awt.Color
import java.util.*

class Help : Command() {
    override val usages = arrayOf("help - lists all the available commands and their usage")

    private fun getUsageTexts(bot: DiscordBot, prefix: String) =
            bot.commands.values.joinToString("\n") { getUsageString(prefix, it.usages) }

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val shard = event.jda
        val guild = event.guild
        val text = getUsageTexts(bot, getGuildSettings(guild, bot.config).prefix)
        val texts = text.split("\n").sorted()
        val embedTexts = texts.chunked(texts.size / 2).map { it.joinToString("\n") }
        val embeds = embedTexts.mapIndexed { i, elem ->
            val builder = EmbedBuilder()
            if (i == 0) {
                builder.setAuthor("Safety Jim - Commands", null, shard.selfUser.avatarUrl)
            }

            builder.setDescription(elem)
            builder.setColor(Color(0x4286F4))
            builder.build()
        }

        event.message.successReact(bot)
        embeds.forEach {
            event.channel.sendMessage(it)
        }
        return false
    }
}
