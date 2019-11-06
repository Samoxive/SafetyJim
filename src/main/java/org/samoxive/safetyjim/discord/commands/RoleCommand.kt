package org.samoxive.safetyjim.discord.commands

import java.util.*
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.RoleEntity
import org.samoxive.safetyjim.database.RolesTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*

class RoleCommand : Command() {
    override val usages = arrayOf("role add <roleName> - adds a new self-assignable role", "role remove <roleName> - removes a self-assignable role")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member!!
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
            message.failMessage("You don't have enough permissions to execute this command! Required permission: Administrator")
            return false
        }

        val roleName = messageIterator.seekToEnd().toLowerCase()

        if (roleName == "") {
            return true
        }

        val matchingRoles = guild.roles
                .filter { role -> role.name.toLowerCase() == roleName }

        if (matchingRoles.isEmpty()) {
            message.failMessage("Could not find a role with specified name!")
            return false
        }

        val matchedRole = matchingRoles[0]

        val record = RolesTable.fetchRole(guild, matchedRole)
        if (subcommand == "add") {
            if (record == null) {
                RolesTable.insertRole(
                        RoleEntity(
                                guildId = guild.idLong,
                                roleId = matchedRole.idLong
                        )
                )
                message.successReact()
            } else {
                message.failMessage("Specified role is already in self-assignable roles list!")
                return false
            }
        } else {
            if (record == null) {
                message.failMessage("Specified role is not in self-assignable roles list!")
                return false
            } else {
                RolesTable.deleteRole(record)
                message.successReact()
            }
        }

        return false
    }
}
