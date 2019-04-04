package org.samoxive.safetyjim.database

import com.uchuhimo.konf.Config
import io.reactiverse.kotlin.pgclient.commitAwait
import io.reactiverse.kotlin.pgclient.getConnectionAwait
import io.reactiverse.kotlin.pgclient.queryAwait
import io.reactiverse.pgclient.PgClient
import io.reactiverse.pgclient.PgPool
import io.reactiverse.pgclient.PgPoolOptions
import kotlinx.coroutines.runBlocking
import org.samoxive.safetyjim.config.DatabaseConfig
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
        WarnsTable
)

fun initPgPool(config: Config) {
    pgPool = PgClient.pool(
            PgPoolOptions()
                    .setPort(config[DatabaseConfig.port])
                    .setHost(config[DatabaseConfig.host])
                    .setDatabase(config[DatabaseConfig.database])
                    .setUser(config[DatabaseConfig.user])
                    .setPassword(config[DatabaseConfig.pass])
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
            System.exit(1)
        }
    }
}


