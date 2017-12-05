package org.samoxive.safetyjim;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.log4j.*;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.metrics.Metrics;
import org.samoxive.safetyjim.server.Server;

import java.io.IOException;
import java.io.OutputStreamWriter;

public class Main {
    public static void main(String ...args) {
        setupLoggers();

        Config config = null;
        try {
            config = Config.fromFileName("config.toml");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        Metrics metrics = new Metrics("jim", "localhost", 8125, config.metrics.enabled);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:postgresql://" +
                                config.database.host +
                                ":" + config.database.port +
                                "/" + config.database.name);
        hikariConfig.setUsername(config.database.user);
        hikariConfig.setPassword(config.database.pass);
        hikariConfig.setConnectionTestQuery("SELECT 1;");
        HikariDataSource ds = new HikariDataSource(hikariConfig);
        DSLContext database = DSL.using(ds, SQLDialect.POSTGRES);

        DiscordBot bot = new DiscordBot(database, config, metrics);
        Server server = new Server();

    }

    public static void setupLoggers() {
        Layout layout = new EnhancedPatternLayout("%d{ISO8601} [%-5p] [%t]: %m%n");
        ConsoleAppender ca = new ConsoleAppender(layout);
        ca.setWriter(new OutputStreamWriter(System.out));

        DailyRollingFileAppender fa = null;

        try {
            fa = new DailyRollingFileAppender(layout, "logs/jim.log", "'.'yyyy-MM-dd");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        Logger.getLogger("com.joestelmach.natty.Parser").setLevel(Level.WARN);
        Logger.getLogger("org.jooq.Constants").setLevel(Level.WARN);
        Logger.getRootLogger().addAppender(fa);
        Logger.getRootLogger().addAppender(ca);
        Logger.getRootLogger().setLevel(Level.INFO);
    }
}
