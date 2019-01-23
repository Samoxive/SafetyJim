package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.tryhard
import java.awt.Color
import java.util.*

class Settings : Command() {
    override val usages = arrayOf("settings display - shows current state of settings", "settings list - lists the keys you can use to customize the bot", "settings reset - resets every setting to their default value", "settings set <key> <value> - changes given key\'s value")

    private val settingKeys = arrayOf("modlog", "modlogchannel", "holdingroomrole", "holdingroom", "holdingroomminutes", "prefix", "welcomemessage", "message", "welcomemessagechannel", "invitelinkremover", "silentcommands", "nospaceprefix", "statistics", "joincaptcha")

    private val settingsListString = """
            |`HoldingRoom <enabled/disabled>` - Default: disabled
            |`JoinCaptcha <enabled/disabled>` - Default: disabled
            |`HoldingRoomMinutes <number>` - Default: 3
            |`HoldingRoomRole <text>` - Default: None
            |`ModLog <enabled/disabled>` - Default: disabled
            |`ModLogChannel <#channel>` - Default: %s
            |`Prefix <text>` - Default: -mod
            |`WelcomeMessage <enabled/disabled>` - Default: disabled
            |`WelcomeMessageChannel <#channel>` - Default: %s
            |`Message <text>` - Default: $DEFAULT_WELCOME_MESSAGE
            |`InviteLinkRemover <enabled/disabled>` - Default: disabled
            |`SilentCommands <enabled/disabled>` - Default: disabled
            |`NoSpacePrefix <enabled/disabled>` - Default: disabled
            |`Statistics <enabled/disabled>` - Default: disabled""".trimMargin()

    private fun handleSettingsDisplay(bot: DiscordBot, event: GuildMessageReceivedEvent) {
        val shard = event.jda
        val channel = event.channel
        val message = event.message
        val selfUser = shard.selfUser
        val output = getSettingsString(bot, event)

        val embed = EmbedBuilder()
        embed.setAuthor("Safety Jim", null, selfUser.avatarUrl)
        embed.addField("Guild Settings", output, false)
        embed.setColor(Color(0x4286F4))

        message.successReact(bot)
        channel.trySendMessage(embed.build())
    }

    private fun getSettingsString(bot: DiscordBot, event: GuildMessageReceivedEvent): String {
        val guild = event.guild

        val config = getGuildSettings(guild, bot.config)
        val output = StringJoiner("\n")

        if (!config.modlog) {
            output.add("**Mod Log:** Disabled")
        } else {
            val modLogChannel = guild.getTextChannelById(config.modlogchannelid)
            output.add("**Mod Log:** Enabled")
            output.add("\t**Mod Log Channel:** " + if (modLogChannel == null) "null" else modLogChannel.asMention)
        }

        if (!config.welcomemessage) {
            output.add("**Welcome Messages:** Disabled")
        } else {
            val welcomeMessageChannel = guild.getTextChannelById(config.welcomemessagechannelid)
            output.add("**Welcome Messages:** Enabled")
            output.add("\t**Welcome Message Channel:** " + if (welcomeMessageChannel == null) "null" else welcomeMessageChannel.asMention)
        }

        if (!config.holdingroom) {
            output.add("**Holding Room:** Disabled")
        } else {
            val holdingRoomMinutes = config.holdingroomminutes
            val holdingRoomRoleId = config.holdingroomroleid
            val holdingRoomRole = if (holdingRoomRoleId != null) guild.getRoleById(holdingRoomRoleId) else null
            output.add("**Holding Room:** Enabled")
            output.add("\t**Holding Room Role:** " + if (holdingRoomRole == null) "null" else holdingRoomRole.name)
            output.add("\t**Holding Room Delay:** $holdingRoomMinutes minute(s)")
        }

        if (!config.joincaptcha) {
            output.add("**Join Captcha:** Disabled")
        } else {
            val holdingRoomRoleId = config.holdingroomroleid
            val holdingRoomRole = if (holdingRoomRoleId != null) guild.getRoleById(holdingRoomRoleId) else null
            output.add("**Join Captcha:** Enabled")
            output.add("\t**Holding Room Role:** " + if (holdingRoomRole == null) "null" else holdingRoomRole.name)
        }

        if (config.invitelinkremover) {
            output.add("**Invite Link Remover:** Enabled")
        } else {
            output.add("**Invite Link Remover:** Disabled")
        }

        if (config.silentcommands) {
            output.add("**Silent Commands:** Enabled")
        } else {
            output.add("**Silent Commands:** Disabled")
        }

        if (config.nospaceprefix) {
            output.add("**No Space Prefix:** Enabled")
        } else {
            output.add("**No Space Prefix:** Disabled")
        }

        if (config.statistics) {
            output.add("**Statistics:** Enabled")
        } else {
            output.add("**Statistics:** Disabled")
        }
        return output.toString()
    }

    @Throws(BadInputException::class)
    private fun isEnabledInput(input: String): Boolean {
        return when (input) {
            "enabled" -> true
            "disabled" -> false
            else -> throw BadInputException()
        }
    }

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)

        val shard = event.jda

        val member = event.member
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfUser = shard.selfUser

        if (!messageIterator.hasNext()) {
            return true
        }

        val subCommand = messageIterator.next()

        if (subCommand == "list") {
            val defaultChannelMention = guild.getDefaultChannelTalkable().asMention
            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim", null, selfUser.avatarUrl)
            embed.addField("List of settings", String.format(settingsListString, defaultChannelMention, defaultChannelMention), false)
            embed.setColor(Color(0x4286F4))
            message.successReact(bot)
            channel.trySendMessage(embed.build())
            return false
        }

        if (subCommand == "display") {
            handleSettingsDisplay(bot, event)
            return false
        }

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            message.failMessage(bot, "You don't have enough permissions to modify guild settings! Required permission: Administrator")
            return false
        }

        if (subCommand == "reset") {
            deleteGuildSettings(guild)
            createGuildSettings(guild, bot.config)

            message.successReact(bot)
            return false
        }

        if (subCommand != "set") {
            return true
        }

        if (!messageIterator.hasNext()) {
            return true
        }

        val key = messageIterator.next().toLowerCase()
        var argument = messageIterator.seekToEnd()
        val argumentSplit = argument.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()

        if (argument == "") {
            return true
        }

        var isKeyOkay = false
        for (possibleKey in settingKeys) {
            if (possibleKey == key) {
                isKeyOkay = true
            }
        }

        if (!isKeyOkay) {
            message.failMessage(bot, "Please enter a valid setting key!")
            return false
        }

        val guildSettings = getGuildSettings(guild, bot.config)

        val failedSyntax = transaction {
            try {
                when (key) {
                    "silentcommands" -> guildSettings.silentcommands = isEnabledInput(argument)
                    "invitelinkremover" -> guildSettings.invitelinkremover = isEnabledInput(argument)
                    "welcomemessage" -> guildSettings.welcomemessage = isEnabledInput(argument)
                    "modlog" -> guildSettings.modlog = isEnabledInput(argument)
                    "welcomemessagechannel" -> {
                        argument = argumentSplit[0]

                        if (!CHANNEL_MENTION_PATTERN.matcher(argument).matches()) {
                            return@transaction true
                        }

                        val argumentChannel = message.mentionedChannels[0]
                        guildSettings.welcomemessagechannelid = argumentChannel.idLong
                    }
                    "modlogchannel" -> {
                        argument = argumentSplit[0]

                        if (!CHANNEL_MENTION_PATTERN.matcher(argument).matches()) {
                            return@transaction true
                        }

                        val argumentChannel = message.mentionedChannels[0]
                        guildSettings.modlogchannelid = argumentChannel.idLong
                    }
                    "holdingroomminutes" -> {
                        val minutes = tryhard { Integer.parseInt(argumentSplit[0]) } ?: return@transaction true
                        guildSettings.holdingroomminutes = minutes
                    }
                    "prefix" -> guildSettings.prefix = argumentSplit[0]
                    "message" -> guildSettings.message = argument
                    "holdingroom" -> {
                        val holdingRoomEnabled = isEnabledInput(argument)
                        val roleId = guildSettings.holdingroomroleid

                        if (roleId == null) {
                            message.failMessage(bot, "You can't enable holding room before setting a holding room role first.")
                            return@transaction false
                        }

                        if (holdingRoomEnabled && guildSettings.joincaptcha) {
                            message.failMessage(bot, "You can't enable holding room while join captcha is enabled.")
                            return@transaction false
                        }

                        guildSettings.holdingroom = holdingRoomEnabled
                    }
                    "joincaptcha" -> {
                        val captchaEnabled = isEnabledInput(argument)
                        val roleId = guildSettings.holdingroomroleid

                        if (roleId == null) {
                            message.failMessage(bot, "You can't enable join captcha before setting a role for it first.")
                            return@transaction false
                        }

                        if (captchaEnabled && guildSettings.holdingroom) {
                            message.failMessage(bot, "You can't enable join captcha while holding room is enabled.")
                            return@transaction false
                        }

                        guildSettings.joincaptcha = captchaEnabled
                    }
                    "holdingroomrole" -> {
                        val foundRoles = guild.getRolesByName(argument, true)
                        if (foundRoles.size == 0) {
                            message.failMessage(bot, "Couldn't find the role by name!")
                            return@transaction false
                        }

                        val role = foundRoles[0]
                        guildSettings.holdingroomroleid = role.idLong
                    }
                    "nospaceprefix" -> guildSettings.nospaceprefix = isEnabledInput(argument)
                    "statistics" -> {
                        message.failMessage(bot, "Statistics is a work in progress feature, you can't enable it!")
                        /*
                        guildSettings.statistics = isEnabledInput(argument)
                        val discordShard = bot.shards
                                .find { discordShard -> discordShard.jda === shard }
                        discordShard?.threadPool?.submit { discordShard.populateGuildStatistics(guild) }
                        kickstartStatistics(guild)
                        */
                    }
                    else -> return@transaction true
                }
            } catch (e: BadInputException) {
                return@transaction true
            }
        }

        if (failedSyntax is Boolean) {
            return failedSyntax
        }

        message.successReact(bot)
        return false
    }

    private class BadInputException : Exception()

    companion object {
        fun kickstartStatistics(guild: Guild) {
            JimMemberCount.new {
                val members = guild.members
                val onlineCount = members.stream().filter { member -> member.isOnline() }.count()
                guildid = guild.idLong
                date = Date().time
                onlinecount = onlineCount.toInt()
                count = members.size
            }
        }
    }
}
