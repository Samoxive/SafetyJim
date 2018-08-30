package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimTag
import org.samoxive.safetyjim.database.JimTagTable
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.TextUtils
import java.awt.Color
import java.util.*

class Tag : Command() {
    override val usages = arrayOf("tag list - Shows all tags and responses to user", "tag <name> - Responds with reponse of the given tag", "tag add <name> <response> - Adds a tag with the given name and response", "tag edit <name> <response> - Changes response of tag with given name", "tag remove <name> - Deletes tag with the given name")
    private val subcommands = arrayOf("list", "add", "edit", "remove")

    private fun isSubcommand(s: String): Boolean {
        for (subcommand in subcommands) {
            if (s == subcommand) {
                return true
            }
        }

        return false
    }

    private fun displayTags(bot: DiscordBot, event: GuildMessageReceivedEvent) {
        val shard = event.jda
        val guild = event.guild
        val channel = event.channel
        val message = event.message

        val records = transaction { JimTag.find { JimTagTable.guildid eq guild.id } }

        if (transaction { records.empty() }) {
            DiscordUtils.successReact(bot, message)
            DiscordUtils.sendMessage(channel, "No tags have been added yet!")
            return
        }

        val tagString = StringJoiner("\n")

        for (record in records) {
            tagString.add("\u2022 `" + record.name + "`")
        }

        val embed = EmbedBuilder()
        embed.setAuthor("Safety Jim", null, shard.selfUser.avatarUrl)
        embed.addField("List of tags", TextUtils.truncateForEmbed(tagString.toString()), false)
        embed.setColor(Color(0x4286F4))

        DiscordUtils.successReact(bot, message)
        DiscordUtils.sendMessage(channel, embed.build())
    }

    private fun addTag(bot: DiscordBot, event: GuildMessageReceivedEvent, messageIterator: Scanner) {
        val guild = event.guild
        val message = event.message
        val member = event.member

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to use this command!")
            return
        }

        if (!messageIterator.hasNext()) {
            DiscordUtils.failMessage(bot, message, "Please provide a tag name and a response to create a new tag!")
            return
        }

        val tagName = messageIterator.next()

        if (isSubcommand(tagName)) {
            DiscordUtils.failMessage(bot, message, "You can't create a tag with the same name as a subcommand!")
            return
        }

        val response = TextUtils.seekScannerToEnd(messageIterator)

        if (response == "") {
            DiscordUtils.failMessage(bot, message, "Empty responses aren't allowed!")
            return
        }

        try {
            transaction {
                JimTag.new {
                    guildid = guild.id
                    name = tagName
                    this.response = response
                }
            }
            DiscordUtils.successReact(bot, message)
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Tag `$tagName` already exists!")
        }
    }

    private fun editTag(bot: DiscordBot, event: GuildMessageReceivedEvent, messageIterator: Scanner) {
        val guild = event.guild
        val message = event.message
        val member = event.member

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to use this command!")
            return
        }

        if (!messageIterator.hasNext()) {
            DiscordUtils.failMessage(bot, message, "Please provide a tag name and a response to edit tags!")
            return
        }

        val tagName = messageIterator.next()
        val response = TextUtils.seekScannerToEnd(messageIterator)

        if (response == "") {
            DiscordUtils.failMessage(bot, message, "Empty responses aren't allowed!")
            return
        }

        val record = transaction {
            JimTag.find {
                (JimTagTable.guildid eq guild.id) and (JimTagTable.name eq tagName)
            }.firstOrNull()
        }

        if (record == null) {
            DiscordUtils.failMessage(bot, message, "Tag `$tagName` does not exist!")
            return
        }

        transaction { record.response = response }

        DiscordUtils.successReact(bot, message)
    }

    private fun deleteTag(bot: DiscordBot, event: GuildMessageReceivedEvent, messageIterator: Scanner) {
        val guild = event.guild
        val message = event.message
        val member = event.member

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to use this command!")
            return
        }

        if (!messageIterator.hasNext()) {
            DiscordUtils.failMessage(bot, message, "Please provide a tag name and a response to delete tags!")
            return
        }

        val tagName = messageIterator.next()

        val record = transaction {
            JimTag.find {
                (JimTagTable.guildid eq guild.id) and (JimTagTable.name eq tagName)
            }.firstOrNull()
        }

        if (record == null) {
            DiscordUtils.failMessage(bot, message, "Tag `$tagName` does not exist!")
            return
        }

        transaction { record.delete() }
        DiscordUtils.successReact(bot, message)
    }

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val guild = event.guild
        val message = event.message
        val channel = event.channel

        if (!messageIterator.hasNext()) {
            return true
        }

        val commandOrTag = messageIterator.next()

        when (commandOrTag) {
            "list" -> displayTags(bot, event)
            "add" -> addTag(bot, event, messageIterator)
            "edit" -> editTag(bot, event, messageIterator)
            "remove" -> deleteTag(bot, event, messageIterator)
            else -> {
                val record = transaction {
                    JimTag.find {
                        (JimTagTable.guildid eq guild.id) and (JimTagTable.name eq commandOrTag)
                    }.firstOrNull()
                }

                if (record == null) {
                    DiscordUtils.failMessage(bot, message, "Could not find a tag with that name!")
                    return false
                }

                DiscordUtils.successReact(bot, message)
                DiscordUtils.sendMessage(channel, record.response)
            }
        }

        return false
    }
}
