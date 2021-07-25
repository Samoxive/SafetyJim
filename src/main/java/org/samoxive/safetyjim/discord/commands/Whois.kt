package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.tryhardAsync
import java.awt.Color
import java.util.*

private fun createUserEmbed(user: User): MessageEmbed {
    val flagString = if (user.flags.isNotEmpty()) {
        user.flags.joinToString(" ") { it.name }
    } else {
        "<none>"
    }
    return EmbedBuilder()
        .setAuthor(user.getTag(), null, user.effectiveAvatarUrl)
        .setTitle("Discord User")
        .addField("ID", user.id, false)
        .addField("User Flags", flagString, false)
        .addField("Registered On", "<t:${user.timeCreated.toEpochSecond()}>", false)
        .setColor(Color(0x4286F4))
        .build()
}

private fun createMemberEmbed(member: Member): MessageEmbed {
    val user = member.user

    val boostStr = if (member.timeBoosted != null) {
        "Since <t:${member.timeBoosted?.toEpochSecond()}>"
    } else {
        "Not Boosting"
    }

    val flagString = if (user.flags.isNotEmpty()) {
        user.flags.joinToString(" ") { it.name }
    } else {
        "<none>"
    }

    val akaString = if (member.nickname != null) {
        " - ${member.nickname}"
    } else {
        ""
    }

    val title = if (member.isOwner) {
        "Owner of ${member.guild.name}"
    } else {
        "Member of ${member.guild.name}"
    }

    return EmbedBuilder()
        .setAuthor("${user.getTag()}$akaString", null, user.effectiveAvatarUrl)
        .setTitle(title)
        .addField("ID", user.id, false)
        .addField("User Flags", flagString, false)
        .addField("Registered On", "<t:${user.timeCreated.toEpochSecond()}>", true)
        .addField("Joined On", "<t:${member.timeJoined.toEpochSecond()}>", true)
        .addField("Boost Status", boostStr, true)
        .setColor(Color(0x4286F4))
        .build()
}

class Whois : Command() {
    override val usages = arrayOf("whois @user - displays information about given user.")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)
        val message = event.message
        val channel = message.channel
        val guild = event.guild
        val jda = event.jda

        if (!messageIterator.hasNext()) {
            return true
        }

        val input = messageIterator.next()
        val userId = getMentionId(input) ?: return true

        val member = tryhardAsync { guild.retrieveMemberById(userId, true).await() }
        val user = if (member != null) {
            member.user
        } else {
            tryhardAsync { jda.retrieveUserById(userId, true).await() }
        }

        if (user == null) {
            message.failMessage("Could not find user to query!")
            return false
        }

        val embed = if (member != null) {
            createMemberEmbed(member)
        } else {
            createUserEmbed(user)
        }
        message.successReact()
        channel.trySendMessage(embed)
        return false
    }
}
