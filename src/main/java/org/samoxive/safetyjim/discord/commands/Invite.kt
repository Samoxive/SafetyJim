package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils

import java.awt.*

class Invite : Command() {
    override val usages = arrayOf("invite - provides the invite link for Jim")
    private val embedBuilder = EmbedBuilder()
    private var embed: MessageEmbed? = null
    private var embedHasAvatarURL = false
    private val botLink = "https://discordapp.com/oauth2/authorize?client_id=313749262687141888&permissions=268446790&scope=bot"
    private val inviteLink = "https://discord.io/safetyjim"
    /*
    * private embed = {
        author: {
            name: `Safety Jim`,
            icon_url: undefined,
        },
        fields: [
            { name: 'Invite Jim!', value: `[Here](${botLink})`, inline: true },
            { name: 'Join our support server!', value: `[Here](${inviteLink})`, inline: true },
        ],
        color: 0x4286f4,
    };
    * */

    init {
        embedBuilder.addField("Invite Jim!", String.format("[Here](%s)", botLink), true)
        embedBuilder.addField("Join our support server!", String.format("[Here](%s)", inviteLink), true)
        embedBuilder.setColor(Color(0x4286F4))
    }

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val message = event.message
        val channel = event.channel
        val shard = event.jda

        if (!embedHasAvatarURL) {
            embedBuilder.setAuthor("Safety Jim", null, shard.selfUser.avatarUrl)
            embed = embedBuilder.build()
            embedHasAvatarURL = true
        }

        DiscordUtils.successReact(bot, message)
        DiscordUtils.sendMessage(channel, embed!!)

        return false
    }
}
