package org.samoxive.safetyjim.database

import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.sqlclient.commitAwait
import io.vertx.kotlin.sqlclient.executeAwait
import io.vertx.kotlin.sqlclient.getConnectionAwait
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.runBlocking
import org.samoxive.safetyjim.config.Config
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

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
    val connectOptions = PgConnectOptions()
        .setPort(config.database.port)
        .setHost(config.database.host)
        .setDatabase(config.database.database)
        .setUser(config.database.user)
        .setPassword(config.database.pass)

    val poolOptions = PoolOptions().setMaxSize(5)
    pgPool = PgPool.pool(connectOptions, poolOptions)

    runBlocking {
        try {
            val conn = pgPool.getConnectionAwait()
            val tx = conn.begin().await()

            conn.query("set local client_min_messages = error;").executeAwait()
            for (table in tables) {
                conn.query(table.createStatement).executeAwait()
                for (indexStatement in table.createIndexStatements) {
                    conn.query(indexStatement).executeAwait()
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

suspend fun PgPool.preparedQueryAwait(query: String, parameters: Tuple): RowSet<Row> {
    return preparedQuery(query).executeAwait(parameters)
}
