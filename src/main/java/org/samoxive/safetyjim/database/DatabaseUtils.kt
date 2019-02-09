package org.samoxive.safetyjim.database

import com.uchuhimo.konf.Config
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import net.dv8tion.jda.core.entities.Guild
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils.createIndex
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.discord.getDefaultChannelTalkable
import org.samoxive.safetyjim.tryhard
import org.samoxive.safetyjim.tryhardAsync
import java.util.concurrent.Executors
import javax.sql.DataSource
import kotlin.coroutines.suspendCoroutine

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

suspend fun getGuildSettings(guild: Guild, config: Config): JimSettings = awaitTransaction {
    val setting = JimSettings.findById(guild.idLong)

    if (setting != null) {
        return@awaitTransaction setting
    }

    createGuildSettings(guild, config)
    JimSettings[guild.idLong]
}

suspend fun getAllGuildSettings() = awaitTransaction { JimSettings.all().associateBy { it -> it.id.value } }

suspend fun deleteGuildSettings(guild: Guild) = awaitTransaction { JimSettings.findById(guild.idLong)?.delete() }

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
            joincaptcha = false
        }
    }
}

suspend fun createGuildSettingsAsync(guild: Guild, config: Config) {
    GlobalScope.async(TRANSACTION_THREAD_POOL) { createGuildSettings(guild, config) }.await()
}

val TRANSACTION_THREAD_POOL = Executors.newFixedThreadPool(16).asCoroutineDispatcher()

suspend fun <T> awaitTransaction(statement: Transaction.() -> T): T = GlobalScope.async(TRANSACTION_THREAD_POOL) {
    transaction(null, statement)
}.await()

suspend fun <T> tryAwaitTransaction(statement: Transaction.() -> T): T? = tryhardAsync {
    awaitTransaction(statement)
}
