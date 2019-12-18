package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.commitAwait
import io.reactiverse.kotlin.pgclient.getConnectionAwait
import io.reactiverse.kotlin.pgclient.queryAwait
import io.reactiverse.pgclient.PgClient
import io.reactiverse.pgclient.PgPool
import io.reactiverse.pgclient.PgPoolOptions
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.samoxive.safetyjim.config.Config
import org.slf4j.LoggerFactory

lateinit var pgPool: PgPool

private val tables = arrayOf(
    BansTable,
    HardbansTable,
    JoinsTable,
    KicksTable,
    MutesTable,
    RemindersTable,
    RolesTable,
    SettingsTable,
    SoftbansTable,
    TagsTable,
    WarnsTable,
    UUIDBlacklistTable,
    UserSecretsTable
)

fun initPgPool(config: Config) {
    pgPool = PgClient.pool(
        PgPoolOptions()
            .setPort(config.database.port)
            .setHost(config.database.host)
            .setDatabase(config.database.database)
            .setUser(config.database.user)
            .setPassword(config.database.pass)
    )

    runBlocking {
        try {
            val conn = pgPool.getConnectionAwait()
            val tx = conn.begin()

            conn.queryAwait("set local client_min_messages = error;")
            for (table in tables) {
                conn.queryAwait(table.createStatement)
                for (indexStatement in table.createIndexStatements) {
                    conn.queryAwait(indexStatement)
                }
            }

            tx.commitAwait()
            conn.close()
        } catch (e: Throwable) {
            LoggerFactory.getLogger("PgPool").error("Failed to initiate tables!", e)
            exitProcess(1)
        }
    }
}
