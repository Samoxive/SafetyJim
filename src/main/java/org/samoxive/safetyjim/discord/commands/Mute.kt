package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.PermissionOverride
import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimMute
import org.samoxive.safetyjim.database.JimMuteTable
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.TextUtils
import java.awt.Color
import java.util.*

class Mute : Command() {
    override val usages = arrayOf("mute @user [reason] | [time] - mutes the user with specific args. Both arguments can be omitted.")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val shard = event.jda

        val member = event.member
        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Manage Roles")
            return false
        }

        if (!messageIterator.hasNext(DiscordUtils.USER_MENTION_PATTERN)) {
            return true
        } else {
            // advance the scanner one step to get rid of user mention
            messageIterator.next()
        }

        val mentionedUsers = message.mentionedUsers
        if (mentionedUsers.isEmpty()) {
            DiscordUtils.failMessage(bot, message, "Could not find the user to mute!")
            return false
        }
        val muteUser = mentionedUsers[0]
        val muteMember = guild.getMember(muteUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        if (user.id == muteUser.id) {
            DiscordUtils.failMessage(bot, message, "You can't mute yourself, dummy!")
            return false
        }

        if (user.id == selfMember.user.id) {
            DiscordUtils.failMessage(bot, message, "Now that's just rude. (I can't mute myself)")
            return false
        }

        val mutedRole = try {
            setupMutedRole(guild)
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Could not create a Muted role, do I have enough permissions?")
            return false
        }

        val parsedReasonAndTime = try {
            TextUtils.getTextAndTime(messageIterator)
        } catch (e: TextUtils.InvalidTimeInputException) {
            DiscordUtils.failMessage(bot, message, "Invalid time argument. Please try again.")
            return false
        } catch (e: TextUtils.TimeInputInPastException) {
            DiscordUtils.failMessage(bot, message, "Your time argument was set for the past. Try again.\n" + "If you're specifying a date, e.g. `30 December`, make sure you also write the year.")
            return false
        }

        val (text, expirationDate) = parsedReasonAndTime
        val reason = if (text == "") "No reason specified" else text
        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Muted in " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were muted in " + guild.name)
        embed.addField("Reason:", TextUtils.truncateForEmbed(reason), false)
        embed.addField("Muted until", expirationDate?.toString() ?: "Indefinitely", false)
        embed.setFooter("Muted by " + DiscordUtils.getUserTagAndId(user), null)
        embed.setTimestamp(now.toInstant())

        DiscordUtils.sendDM(muteUser, embed.build())

        try {
            controller.addSingleRoleToMember(muteMember, mutedRole!!).complete()
            DiscordUtils.successReact(bot, message)

            val expires = expirationDate != null
            val record = transaction {
                JimMute.find {
                    (JimMuteTable.guildid eq guild.id) and (JimMuteTable.userid eq muteUser.id)
                }.forUpdate().forEach { it.unmuted = true }

                JimMute.new {
                    userid = muteUser.id
                    moderatoruserid = user.id
                    guildid = guild.id
                    mutetime = now.time / 1000
                    expiretime = if (expirationDate == null) 0 else expirationDate.time / 1000
                    this.reason = reason
                    this.expires = expires
                    unmuted = false
                }
            }

            DiscordUtils.createModLogEntry(bot, shard, message, muteMember, reason, "mute", record.id.value, expirationDate, true)
            DiscordUtils.sendMessage(channel, "Muted " + DiscordUtils.getUserTagAndId(muteUser))
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Could not mute the specified user. Do I have enough permissions?")
        }

        return false
    }

    companion object {

        fun setupMutedRole(guild: Guild): Role {
            val controller = guild.controller
            val channels = guild.textChannels
            val roleList = guild.roles
            var mutedRole: Role? = null

            for (role in roleList) {
                if (role.name == "Muted") {
                    mutedRole = role
                    break
                }
            }

            if (mutedRole == null) {
                // Muted role doesn't exist at all, so we need to create one
                // and create channel overrides for the role
                mutedRole = controller.createRole()
                        .setName("Muted")
                        .setPermissions(
                                Permission.MESSAGE_READ,
                                Permission.MESSAGE_HISTORY,
                                Permission.VOICE_CONNECT
                        )
                        .complete()

                for (channel in channels) {
                    channel.createPermissionOverride(mutedRole)
                            .setDeny(
                                    Permission.MESSAGE_WRITE,
                                    Permission.MESSAGE_ADD_REACTION,
                                    Permission.VOICE_SPEAK
                            )
                            .complete()
                }
            }

            for (channel in channels) {
                var override: PermissionOverride? = null
                for (channelOverride in channel.rolePermissionOverrides) {
                    if (channelOverride.role == mutedRole) {
                        override = channelOverride
                        break
                    }
                }

                // This channel is either created after we created a Muted role
                // or its permissions were played with, so we should set it straight
                if (override == null) {
                    channel.createPermissionOverride(mutedRole)
                            .setDeny(
                                    Permission.MESSAGE_WRITE,
                                    Permission.MESSAGE_ADD_REACTION,
                                    Permission.VOICE_SPEAK
                            )
                            .complete()
                }
            }

            // return the found or created muted role so command can use it
            return mutedRole!!
        }
    }
}
