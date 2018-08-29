package org.samoxive.safetyjim.database

import net.dv8tion.jda.core.entities.Guild
import org.jooq.DSLContext
import org.samoxive.jooq.generated.Tables
import org.samoxive.jooq.generated.tables.records.SettingsRecord
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils

import java.util.HashMap

object DatabaseUtils {
    val DEFAULT_WELCOME_MESSAGE = "Welcome to \$guild \$user!"

    fun getGuildSettings(bot: DiscordBot, database: DSLContext, guild: Guild): SettingsRecord {
        val record = database.selectFrom(Tables.SETTINGS)
                .where(Tables.SETTINGS.GUILDID.eq(guild.id))
                .fetchAny()

        if (record == null) {
            createGuildSettings(bot, database, guild)
        }


        return database.selectFrom(Tables.SETTINGS)
                .where(Tables.SETTINGS.GUILDID.eq(guild.id))
                .fetchAny()
    }

    fun getAllGuildSettings(database: DSLContext): Map<String, SettingsRecord> {
        val map = HashMap<String, SettingsRecord>()
        val records = database.selectFrom(Tables.SETTINGS).fetch()

        for (record in records) {
            map[record.guildid] = record
        }

        return map
    }

    fun deleteGuildSettings(database: DSLContext, guild: Guild) {
        database.deleteFrom(Tables.SETTINGS).where(Tables.SETTINGS.GUILDID.eq(guild.id)).execute()
    }

    fun createGuildSettings(bot: DiscordBot, database: DSLContext, guild: Guild) {
        val record = database.newRecord(Tables.SETTINGS)

        record.guildid = guild.id
        record.silentcommands = false
        record.invitelinkremover = false
        record.modlog = false
        record.modlogchannelid = DiscordUtils.getDefaultChannel(guild).id
        record.holdingroom = false
        record.holdingroomroleid = null
        record.holdingroomminutes = 3
        record.prefix = bot.config[JimConfig.default_prefix]
        record.welcomemessage = false
        record.message = DEFAULT_WELCOME_MESSAGE
        record.welcomemessagechannelid = DiscordUtils.getDefaultChannel(guild).id
        record.nospaceprefix = false
        record.statistics = false

        record.store()
    }
}
