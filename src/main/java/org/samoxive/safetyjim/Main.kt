package org.samoxive.safetyjim

import com.uchuhimo.konf.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.apache.log4j.*
import org.samoxive.safetyjim.config.DatabaseConfig
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.config.OauthConfig
import org.samoxive.safetyjim.config.ServerConfig
import org.samoxive.safetyjim.database.initPgPool
import org.samoxive.safetyjim.database.pgPool
import org.samoxive.safetyjim.database.setupDatabase
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.Server
import org.slf4j.LoggerFactory
import java.io.OutputStreamWriter

fun main() {
    setupLoggers()

    val config = Config {
        addSpec(JimConfig)
        addSpec(DatabaseConfig)
        addSpec(OauthConfig)
        addSpec(ServerConfig)
    }.from.toml.file("config.toml")

    val hikariConfig = HikariConfig()
    hikariConfig.jdbcUrl = config[DatabaseConfig.jdbc_url]
    hikariConfig.username = config[DatabaseConfig.user]
    hikariConfig.password = config[DatabaseConfig.pass]
    hikariConfig.connectionTestQuery = "SELECT 1;"
    val ds = HikariDataSource(hikariConfig)
    setupDatabase(ds)
    initPgPool(config)
    val bot = DiscordBot(config)
    Server(bot)
}

fun setupLoggers() {
    val layout = EnhancedPatternLayout("%d{ISO8601} [%-5p] [%t]: %m%n")
    val ca = ConsoleAppender(layout)
    ca.setWriter(OutputStreamWriter(System.out))

    val log = LoggerFactory.getLogger("main")
    val fa = try {
        DailyRollingFileAppender(layout, "logs/jim.log", "'.'yyyy-MM-dd")
    } catch (e: Exception) {
        log.error("Could not access log files!", e)
        return System.exit(1)
    }

    Logger.getLogger("com.joestelmach.natty.Parser").level = Level.WARN
    Logger.getLogger("org.jooq.Constants").level = Level.WARN
    Logger.getRootLogger().addAppender(fa)
    Logger.getRootLogger().addAppender(ca)
    Logger.getRootLogger().level = Level.INFO
}