package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.samoxive.safetyjim.database.JimRole
import org.samoxive.safetyjim.database.JimRoleTable
import org.samoxive.safetyjim.database.JimSettings
import org.samoxive.safetyjim.database.awaitTransaction
import org.samoxive.safetyjim.discord.*
import java.util.*

class Iam : Command() {
    override val usages = arrayOf("iam <roleName> - self assigns specified role, removes role if it is already assigned")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: JimSettings, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member
        val message = event.message
        val guild = event.guild

        val roleName = messageIterator.seekToEnd()
                .toLowerCase()

        if (roleName == "") {
            return true
        }

        val matchingRoles = guild.roles
                .filter { role -> role.name.toLowerCase() == roleName }

        if (matchingRoles.isEmpty()) {
            message.failMessage(bot, "Could not find a role with specified name!")
            return false
        }

        val matchedRole = matchingRoles[0]
        var roleExists = false
        awaitTransaction {
            JimRole.find {
                (JimRoleTable.guildid eq guild.idLong) and (JimRoleTable.roleid eq matchedRole.idLong)
            }.forEach {
                if (it.roleid == matchedRole.idLong) {
                    roleExists = true
                }
            }
        }

        if (!roleExists) {
            message.failMessage(bot, "This role is not self-assignable!")
            return false
        }

        val controller = guild.controller
        if (member.roles.find { it == matchedRole } != null) {
            try {
                controller.removeSingleRoleFromMember(member, matchedRole).await()
                message.successReact(bot)
            } catch (e: Exception) {
                message.failMessage(bot, "Could not remove specified role. Do I have enough permissions?")
            }
        } else {
            try {
                controller.addSingleRoleToMember(member, matchedRole).await()
                message.successReact(bot)
            } catch (e: Exception) {
                message.failMessage(bot, "Could not assign specified role. Do I have enough permissions?")
            }
        }

        return false
    }
}
