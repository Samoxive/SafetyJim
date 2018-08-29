package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.jooq.generated.Tables
import org.samoxive.jooq.generated.tables.records.RolelistRecord
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.TextUtils
import java.util.Scanner

class RoleCommand : Command() {
    override val usages = arrayOf("role add <roleName> - adds a new self-assignable role", "role remove <roleName> - removes a self-assignable role")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val database = bot.database

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

        val roleName = TextUtils.seekScannerToEnd(messageIterator).toLowerCase()

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

        if (subcommand == "add") {
            var record: RolelistRecord? = database.selectFrom(Tables.ROLELIST)
                    .where(Tables.ROLELIST.GUILDID.eq(guild.id))
                    .and(Tables.ROLELIST.ROLEID.eq(matchedRole.id))
                    .fetchAny()

            if (record == null) {
                record = database.newRecord(Tables.ROLELIST)
                record!!.guildid = guild.id
                record.roleid = matchedRole.id
                record.store()
                DiscordUtils.successReact(bot, message)
            } else {
                DiscordUtils.failMessage(bot, message, "Specified role is already in self-assignable roles list!")
                return false
            }
        } else {
            val record = database.selectFrom(Tables.ROLELIST)
                    .where(Tables.ROLELIST.GUILDID.eq(guild.id))
                    .and(Tables.ROLELIST.ROLEID.eq(matchedRole.id))
                    .fetchAny()

            if (record == null) {
                DiscordUtils.failMessage(bot, message, "Specified role is not in self-assignable roles list!")
                return false
            } else {
                record.delete()
                DiscordUtils.successReact(bot, message)
            }
        }

        return false
    }
}
