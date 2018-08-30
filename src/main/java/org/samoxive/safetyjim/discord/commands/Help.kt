package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.getGuildSettings
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import java.awt.Color
import java.util.*

class Help : Command() {
    override val usages = arrayOf("help - lists all the available commands and their usage")
    private var embed: MessageEmbed? = null

    private fun getUsageTexts(bot: DiscordBot, prefix: String): String {
        val joiner = StringJoiner("\n")
        val commandList = bot.commands

        for (command in commandList.values) {
            joiner.add(DiscordUtils.getUsageString(prefix, command.usages))
        }

        return joiner.toString()
    }

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        if (embed == null) {
            val shard = event.jda
            val guild = event.guild
            val builder = EmbedBuilder()
            builder.setAuthor("Safety Jim - Commands", null, shard.selfUser.avatarUrl)
            builder.setDescription(getUsageTexts(bot, getGuildSettings(guild, bot.config).prefix))
            builder.setColor(Color(0x4286F4))

            embed = builder.build()
        }

        DiscordUtils.successReact(bot, event.message)
        DiscordUtils.sendMessage(event.channel, embed!!)
        return false
    }
}
