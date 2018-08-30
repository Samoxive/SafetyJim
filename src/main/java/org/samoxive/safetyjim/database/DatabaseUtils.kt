package org.samoxive.safetyjim.database

import com.uchuhimo.konf.Config
import net.dv8tion.jda.core.entities.Guild
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.SchemaUtils.createIndex
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.discord.DiscordUtils
import javax.sql.DataSource

fun setupDatabase(dataSource: DataSource) {
    Database.connect(dataSource)

    transaction {
        createMissingTablesAndColumns(
                JimBanTable,
                JimCommandLogTable,
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
    }
}

const val DEFAULT_WELCOME_MESSAGE = "Welcome to \$guild \$user!"

fun getGuildSettings(guild: Guild, config: Config): JimSettings = transaction {
    val setting = JimSettings.findById(guild.id)

    if (setting == null) {
        createGuildSettings(guild, config)
    }

    JimSettings[guild.id]
}

fun getAllGuildSettings() = transaction { JimSettings.all().associateBy { it -> it.id.value } }

fun deleteGuildSettings(guild: Guild) = transaction { JimSettings.findById(guild.id)?.delete() }

fun createGuildSettings(guild: Guild, config: Config) = transaction {
    JimSettings.new(guild.id) {
        silentcommands = false
        invitelinkremover = false
        modlog = false
        modlogchannelid = DiscordUtils.getDefaultChannel(guild).id
        holdingroom = false
        holdingroomroleid = null
        holdingroomminutes = 3
        prefix = config[JimConfig.default_prefix]
        welcomemessage = false
        message = DEFAULT_WELCOME_MESSAGE
        welcomemessagechannelid = DiscordUtils.getDefaultChannel(guild).id
        nospaceprefix = false
        statistics = false
    }
}
