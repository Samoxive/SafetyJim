package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

class Settings : Command() {
    override val usages = arrayOf("settings display - shows current state of settings", "settings list - lists the keys you can use to customize the bot", "settings reset - resets every setting to their default value", "settings set <key> <value> - changes given key\'s value")

    private val settingKeys = arrayOf("modlog", "modlogchannel", "holdingroomrole", "holdingroom", "holdingroomminutes", "prefix", "welcomemessage", "message", "welcomemessagechannel", "invitelinkremover", "silentcommands", "nospaceprefix", "statistics")

    private val settingsListString = """
            |`HoldingRoom <enabled/disabled>` - Default: disabled
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

        DiscordUtils.successReact(bot, message)
        DiscordUtils.sendMessage(channel, embed.build())
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
            val holdingRoomRole = guild.getRoleById(holdingRoomRoleId)
            output.add("**Holding Room:** Enabled")
            output.add("\t**Holding Room Role:** " + if (holdingRoomRole == null) "null" else holdingRoomRole.name)
            output.add("\t**Holding Room Delay:** $holdingRoomMinutes minute(s)")
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
            val defaultChannelMention = DiscordUtils.getDefaultChannel(guild).asMention
            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim", null, selfUser.avatarUrl)
            embed.addField("List of settings", String.format(settingsListString, defaultChannelMention, defaultChannelMention), false)
            embed.setColor(Color(0x4286F4))
            DiscordUtils.successReact(bot, message)
            DiscordUtils.sendMessage(channel, embed.build())
            return false
        }

        if (subCommand == "display") {
            handleSettingsDisplay(bot, event)
            return false
        }

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to modify guild settings! Required permission: Administrator")
            return false
        }

        if (subCommand == "reset") {
            deleteGuildSettings(guild)
            createGuildSettings(guild, bot.config)
            DiscordUtils.successReact(bot, message)
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
        val argumentSplit = argument.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

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
            DiscordUtils.failMessage(bot, message, "Please enter a valid setting key!")
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
                        guildSettings.welcomemessagechannelid = argumentChannel.id
                    }
                    "modlogchannel" -> {
                        argument = argumentSplit[0]

                        if (!CHANNEL_MENTION_PATTERN.matcher(argument).matches()) {
                            return@transaction true
                        }

                        val argumentChannel = message.mentionedChannels[0]
                        guildSettings.modlogchannelid = argumentChannel.id
                    }
                    "holdingroomminutes" -> {
                        val minutes = try {
                            Integer.parseInt(argumentSplit[0])
                        } catch (e: NumberFormatException) {
                            return@transaction true
                        }

                        guildSettings.holdingroomminutes = minutes
                    }
                    "prefix" -> guildSettings.prefix = argumentSplit[0]
                    "message" -> guildSettings.message = argument
                    "holdingroom" -> {
                        val holdingRoomEnabled = isEnabledInput(argument)
                        val roleId = guildSettings.holdingroomroleid

                        if (roleId == null) {
                            DiscordUtils.failMessage(bot, message, "You can't enable holding room before setting a role for it first.")
                            return@transaction false
                        }

                        guildSettings.holdingroom = holdingRoomEnabled
                    }
                    "holdingroomrole" -> {
                        val foundRoles = guild.getRolesByName(argument, true)
                        if (foundRoles.size == 0) {
                            DiscordUtils.failMessage(bot, message, "Couldn't find the role by name!")
                            return@transaction false
                        }

                        val role = foundRoles[0]
                        guildSettings.holdingroomroleid = role.id
                    }
                    "nospaceprefix" -> guildSettings.nospaceprefix = isEnabledInput(argument)
                    "statistics" -> {
                        guildSettings.statistics = isEnabledInput(argument)
                        val discordShard = bot.shards
                                .find { discordShard -> discordShard.shard === shard }
                        discordShard?.threadPool?.submit { discordShard.populateGuildStatistics(guild) }
                        kickstartStatistics(guild)
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

        DiscordUtils.successReact(bot, message)
        return false
    }

    private class BadInputException : Exception()

    companion object {

        fun kickstartStatistics(guild: Guild) {
            JimMemberCount.new {
                val members = guild.members
                val onlineCount = members.stream().filter { member -> DiscordUtils.isOnline(member) }.count()
                guildid = guild.id
                date = Date().time
                onlinecount = onlineCount.toInt()
                count = members.size
            }
        }
    }
}
