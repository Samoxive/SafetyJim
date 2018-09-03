package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimRole
import org.samoxive.safetyjim.database.JimRoleTable
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.seekToEnd
import java.util.*

class RoleCommand : Command() {
    override val usages = arrayOf("role add <roleName> - adds a new self-assignable role", "role remove <roleName> - removes a self-assignable role")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member
        val message = event.message
        val guild = event.guild

        if (!messageIterator.hasNext()) {
            return true
        }

        val subcommand = messageIterator.next()
        when (subcommand) {
            "add", "remove" -> {
            }
            else -> return true
        }

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Administrator")
            return false
        }

        val roleName = messageIterator.seekToEnd().toLowerCase()

        if (roleName == "") {
            return true
        }

        val matchingRoles = guild.roles
                .filter { role -> role.name.toLowerCase() == roleName }

        if (matchingRoles.isEmpty()) {
            DiscordUtils.failMessage(bot, message, "Could not find a role with specified name!")
            return false
        }

        val matchedRole = matchingRoles[0]

        val record = transaction {
            JimRole.find {
                (JimRoleTable.guildid eq guild.id) and (JimRoleTable.roleid eq matchedRole.id)
            }.firstOrNull()
        }
        if (subcommand == "add") {
            if (record == null) {
                transaction {
                    JimRole.new {
                        guildid = guild.id
                        roleid = matchedRole.id
                    }
                }
                DiscordUtils.successReact(bot, message)
            } else {
                DiscordUtils.failMessage(bot, message, "Specified role is already in self-assignable roles list!")
                return false
            }
        } else {
            if (record == null) {
                DiscordUtils.failMessage(bot, message, "Specified role is not in self-assignable roles list!")
                return false
            } else {
                transaction { record.delete() }
                DiscordUtils.successReact(bot, message)
            }
        }

        return false
    }
}
