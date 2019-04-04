package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.TagEntity
import org.samoxive.safetyjim.database.TagsTable
import org.samoxive.safetyjim.discord.*
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

    private suspend fun displayTags(bot: DiscordBot, event: GuildMessageReceivedEvent) {
        val shard = event.jda
        val guild = event.guild
        val channel = event.channel
        val message = event.message

        val records = TagsTable.fetchGuildTags(guild)

        if (records.isEmpty()) {
            message.successReact(bot)
            channel.trySendMessage("No tags have been added yet!")
            return
        }

        val tagString = StringJoiner("\n")

        for (record in records) {
            tagString.add("\u2022 `" + record.name + "`")
        }

        val embed = EmbedBuilder()
        embed.setAuthor("Safety Jim", null, shard.selfUser.avatarUrl)
        embed.addField("List of tags", truncateForEmbed(tagString.toString()), false)
        embed.setColor(Color(0x4286F4))

        message.successReact(bot)
        channel.trySendMessage(embed.build())
    }

    private suspend fun addTag(bot: DiscordBot, event: GuildMessageReceivedEvent, messageIterator: Scanner) {
        val guild = event.guild
        val message = event.message
        val member = event.member

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            message.failMessage(bot, "You don't have enough permissions to use this command!")
            return
        }

        if (!messageIterator.hasNext()) {
            message.failMessage(bot, "Please provide a tag name and a response to create a new tag!")
            return
        }

        val tagName = messageIterator.next()

        if (isSubcommand(tagName)) {
            message.failMessage(bot, "You can't create a tag with the same name as a subcommand!")
            return
        }

        val response = messageIterator.seekToEnd()

        if (response == "") {
            message.failMessage(bot, "Empty responses aren't allowed!")
            return
        }

        try {
            TagsTable.insertTag(
                    TagEntity(
                            guildId = guild.idLong,
                            name = tagName,
                            response = response
                    )
            )
            message.successReact(bot)
        } catch (e: Exception) {
            message.failMessage(bot, "Tag `$tagName` already exists!")
        }
    }

    private suspend fun editTag(bot: DiscordBot, event: GuildMessageReceivedEvent, messageIterator: Scanner) {
        val guild = event.guild
        val message = event.message
        val member = event.member

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            message.failMessage(bot, "You don't have enough permissions to use this command!")
            return
        }

        if (!messageIterator.hasNext()) {
            message.failMessage(bot, "Please provide a tag name and a response to edit tags!")
            return
        }

        val tagName = messageIterator.next()
        val response = messageIterator.seekToEnd()

        if (response == "") {
            message.failMessage(bot, "Empty responses aren't allowed!")
            return
        }

        val record = TagsTable.fetchTagByName(guild, tagName)
        if (record == null) {
            message.failMessage(bot, "Tag `$tagName` does not exist!")
            return
        }

        TagsTable.updateTag(record.copy(response = response))
        message.successReact(bot)
    }

    private suspend fun deleteTag(bot: DiscordBot, event: GuildMessageReceivedEvent, messageIterator: Scanner) {
        val guild = event.guild
        val message = event.message
        val member = event.member

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            message.failMessage(bot, "You don't have enough permissions to use this command!")
            return
        }

        if (!messageIterator.hasNext()) {
            message.failMessage(bot, "Please provide a tag name and a response to delete tags!")
            return
        }

        val tagName = messageIterator.next()

        val record = TagsTable.fetchTagByName(guild, tagName)
        if (record == null) {
            message.failMessage(bot, "Tag `$tagName` does not exist!")
            return
        }

        TagsTable.deleteTag(record)
        message.successReact(bot)
    }

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
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
                val record = TagsTable.fetchTagByName(guild, commandOrTag)
                if (record == null) {
                    message.failMessage(bot, "Could not find a tag with that name!")
                    return false
                }

                message.successReact(bot)
                channel.trySendMessage(record.response)
            }
        }

        return false
    }
}
