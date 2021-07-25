package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.tryhardAsync
import java.util.*

class MassBan : Command() {
    override val usages = arrayOf("massban @user1 @user2 ... @userN - mass hardbans given users")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

        val jda = event.jda
        val member = event.member!!
        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage("You don't have enough permissions to execute this command! Required permission: Ban Members")
            return false
        }

        if (args.isEmpty()) {
            return true
        }

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage("I don't have enough permissions to do that!")
            return false
        }

        val mentions = mutableSetOf<Long>()
        while (messageIterator.hasNext()) {
            val input = messageIterator.next()
            val userId = getMentionId(input) ?: return true
            mentions.add(userId)
        }

        val isBanningSelf = mentions.any { it == user.idLong }
        if (isBanningSelf) {
            message.failMessage("You can't ban yourself, dummy!")
            return false
        }

        val targetUsers = mutableSetOf<User>()
        for (mention in mentions) {
            val targetMember = tryhardAsync { guild.retrieveMemberById(mention, true).await() }
            if (targetMember == null) {
                val targetUser = tryhardAsync { jda.retrieveUserById(mention, true).await() } ?: return true
                targetUsers.add(targetUser)
                continue
            }

            if (!targetMember.isBannableBy(selfMember)) {
                message.failMessage("I don't have enough permissions to do that!")
                return false
            }

            targetUsers.add(targetMember.user)
        }

        try {
            for (targetUser in targetUsers) {
                hardbanAction(guild, channel, settings, user, targetUser, "Targeted in mass ban")
            }
            message.successReact()
            channel.sendModActionConfirmationMessage(settings, "Mass banned ${targetUsers.size} user(s).")
        } catch (e: Exception) {
            message.failMessage("Could not ban the specified users. Do I have enough permissions?")
        }

        return false
    }
}
