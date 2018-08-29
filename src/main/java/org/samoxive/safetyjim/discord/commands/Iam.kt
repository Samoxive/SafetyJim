package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.jooq.generated.Tables
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.TextUtils
import java.util.Scanner

class Iam : Command() {
    override val usages = arrayOf("iam <roleName> - self assigns specified role")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val database = bot.database

        val member = event.member
        val message = event.message
        val guild = event.guild

        val roleName = TextUtils.seekScannerToEnd(messageIterator)
                .toLowerCase()

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
        val assignableRoles = database.selectFrom(Tables.ROLELIST)
                .where(Tables.ROLELIST.GUILDID.eq(guild.id))
                .and(Tables.ROLELIST.ROLEID.eq(matchedRole.id))
                .fetch()

        var roleExists = false
        for (record in assignableRoles) {
            if (record.roleid == matchedRole.id) {
                roleExists = true
            }
        }

        if (!roleExists) {
            DiscordUtils.failMessage(bot, message, "This role is not self-assignable!")
            return false
        }

        val controller = guild.controller

        try {
            controller.addSingleRoleToMember(member, matchedRole).complete()
            DiscordUtils.successReact(bot, message)
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Could not assign specified role. Do I have enough permissions?")
        }

        return false
    }
}
