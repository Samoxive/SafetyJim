package org.samoxive.safetyjim

import com.uchuhimo.konf.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.apache.log4j.*
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.samoxive.safetyjim.config.*
import org.samoxive.safetyjim.discord.DiscordBot
import org.slf4j.LoggerFactory

import java.io.OutputStreamWriter

fun main(args: Array<String>) {
    setupLoggers()

    val config = Config {
        addSpec(JimConfig)
        addSpec(DatabaseConfig)
        addSpec(BotListConfig)
        addSpec(OauthConfig)
        addSpec(ServerConfig)
    }.from.toml.file("config.toml")

    val hikariConfig = HikariConfig()
    hikariConfig.jdbcUrl = config[DatabaseConfig.jdbc_url]
    hikariConfig.username = config[DatabaseConfig.user]
    hikariConfig.password = config[DatabaseConfig.pass]
    hikariConfig.connectionTestQuery = "SELECT 1;"
    val ds = HikariDataSource(hikariConfig)
    val database = DSL.using(ds, SQLDialect.POSTGRES)

    val bot = DiscordBot(database, config)
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