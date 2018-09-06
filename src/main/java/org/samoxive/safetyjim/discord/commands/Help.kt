package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.getGuildSettings
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import java.awt.Color
import java.util.*

class Help : Command() {
    override val usages = arrayOf("help - lists all the available commands and their usage")

    private fun getUsageTexts(bot: DiscordBot, prefix: String): String {
        val joiner = StringJoiner("\n")
        val commandList = bot.commands

        for (command in commandList.values) {
            joiner.add(DiscordUtils.getUsageString(prefix, command.usages))
        }

        return joiner.toString()
    }

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

        DiscordUtils.successReact(bot, event.message)
        embeds.forEach {
            DiscordUtils.sendMessage(event.channel, it)
        }
        return false
    }
}
