package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.jooq.generated.Tables
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.TextUtils

import java.awt.*
import java.util.Scanner
import java.util.StringJoiner

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
        val database = bot.database
        val shard = event.jda
        val guild = event.guild
        val channel = event.channel
        val message = event.message

        val records = database.selectFrom(Tables.TAGLIST)
                .where(Tables.TAGLIST.GUILDID.eq(guild.id))
                .fetch()

        if (records.isEmpty()) {
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
        val database = bot.database
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

        val record = database.newRecord(Tables.TAGLIST)

        record.guildid = guild.id
        record.name = tagName
        record.response = response

        try {
            record.store()
            DiscordUtils.successReact(bot, message)
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Tag `$tagName` already exists!")
        }

    }

    private fun editTag(bot: DiscordBot, event: GuildMessageReceivedEvent, messageIterator: Scanner) {
        val database = bot.database
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

        val record = database.selectFrom(Tables.TAGLIST)
                .where(Tables.TAGLIST.GUILDID.eq(guild.id))
                .and(Tables.TAGLIST.NAME.eq(tagName))
                .fetchOne()

        if (record == null) {
            DiscordUtils.failMessage(bot, message, "Tag `$tagName` does not exist!")
            return
        }

        record.response = response
        record.update()

        DiscordUtils.successReact(bot, message)
    }

    private fun deleteTag(bot: DiscordBot, event: GuildMessageReceivedEvent, messageIterator: Scanner) {
        val database = bot.database
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

        val record = database.selectFrom(Tables.TAGLIST)
                .where(Tables.TAGLIST.GUILDID.eq(guild.id))
                .and(Tables.TAGLIST.NAME.eq(tagName))
                .fetchOne()

        if (record == null) {
            DiscordUtils.failMessage(bot, message, "Tag `$tagName` does not exist!")
            return
        }

        record.delete()
        DiscordUtils.successReact(bot, message)
    }

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val database = bot.database
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
                val record = database.selectFrom(Tables.TAGLIST)
                        .where(Tables.TAGLIST.GUILDID.eq(guild.id))
                        .and(Tables.TAGLIST.NAME.eq(commandOrTag))
                        .fetchAny()

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

