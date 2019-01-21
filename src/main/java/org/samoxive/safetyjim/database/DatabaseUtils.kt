package org.samoxive.safetyjim.database

import com.uchuhimo.konf.Config
import net.dv8tion.jda.core.entities.Guild
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.SchemaUtils.createIndex
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.discord.getDefaultChannelTalkable
import org.samoxive.safetyjim.tryhard
import javax.sql.DataSource

fun setupDatabase(dataSource: DataSource) {
    Database.connect(dataSource)

    transaction {
        addLogger(StdOutSqlLogger)
        createMissingTablesAndColumns(
                JimBanTable,
                JimHardbanTable,
                JimJoinTable,
                JimKickTable,
                JimMemberCountTable,
                JimMessageTable,
                JimMuteTable,
                JimReminderTable,
                JimRoleTable,
                JimSettingsTable,
                JimSoftbanTable,
                JimTagTable,
                JimWarnTable
        )

        createIndex(Index(listOf(JimRoleTable.guildid, JimRoleTable.roleid), true))
        createIndex(Index(listOf(JimTagTable.guildid, JimTagTable.name), true))
        createIndex(Index(listOf(JimMessageTable.guildid), false))
        createIndex(Index(listOf(JimMessageTable.guildid, JimMessageTable.channelid), false))
    }
}

const val DEFAULT_WELCOME_MESSAGE = "Welcome to \$guild \$user!"

fun getGuildSettings(guild: Guild, config: Config): JimSettings = transaction {
    val setting = JimSettings.findById(guild.idLong)

    if (setting != null) {
        return@transaction setting
    }

    tryhard { createGuildSettings(guild, config) }
    JimSettings[guild.idLong]
}

fun getAllGuildSettings() = transaction { JimSettings.all().associateBy { it -> it.id.value } }

fun deleteGuildSettings(guild: Guild) = transaction { JimSettings.findById(guild.idLong)?.delete() }

fun createGuildSettings(guild: Guild, config: Config) = tryhard {
    transaction {
        val defaultChannel = guild.getDefaultChannelTalkable()
        JimSettings.new(guild.idLong) {
            silentcommands = false
            invitelinkremover = false
            modlog = false
            modlogchannelid = defaultChannel.idLong
            holdingroom = false
            holdingroomroleid = null
            holdingroomminutes = 3
            prefix = config[JimConfig.default_prefix]
            welcomemessage = false
            message = DEFAULT_WELCOME_MESSAGE
            welcomemessagechannelid = defaultChannel.idLong
            nospaceprefix = false
            statistics = false
        }
    }
}
