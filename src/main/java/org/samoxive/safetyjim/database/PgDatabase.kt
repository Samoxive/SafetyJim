package org.samoxive.safetyjim.database

import com.uchuhimo.konf.Config
import io.reactiverse.pgclient.PgClient
import io.reactiverse.pgclient.PgPool
import io.reactiverse.pgclient.PgPoolOptions
import org.samoxive.safetyjim.config.DatabaseConfig

lateinit var pgPool: PgPool

fun initPgPool(config: Config) {
    pgPool = PgClient.pool(
            PgPoolOptions()
                    .setPort(config[DatabaseConfig.port])
                    .setHost(config[DatabaseConfig.host])
                    .setDatabase(config[DatabaseConfig.database])
                    .setUser(config[DatabaseConfig.user])
                    .setPassword(config[DatabaseConfig.pass])
    )
}