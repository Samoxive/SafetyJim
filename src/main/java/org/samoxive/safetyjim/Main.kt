package org.samoxive.safetyjim

import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.log4j.*
import org.samoxive.safetyjim.config.Config
import org.samoxive.safetyjim.database.initPgPool
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.Server
import org.slf4j.LoggerFactory
import java.io.File
import java.io.OutputStreamWriter
import kotlin.system.exitProcess

fun main() {
    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory")
    setupLoggers()

    val config: Config = objectMapper.readValue(File("./config.json"))

    initPgPool(config)
    initHttpClient()
    val bot = DiscordBot(config)
    Server(bot, vertx)
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
        exitProcess(1)
    }

    Logger.getLogger("com.joestelmach.natty.Parser").level = Level.WARN
    Logger.getRootLogger().addAppender(fa)
    Logger.getRootLogger().addAppender(ca)
    Logger.getRootLogger().level = Level.INFO
}
