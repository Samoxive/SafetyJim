package org.samoxive.safetyjim;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.core.entities.TextChannel;
import org.coursera.metrics.datadog.DatadogReporter;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.Banlist;
import org.samoxive.jooq.generated.tables.records.BanlistRecord;
import org.samoxive.jooq.generated.tables.records.CommandlogsRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;

public class Main {
    public static void main(String ...args) {
        Config config = null;


        try {
            config = Config.fromFileName("config.toml");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

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

        DiscordBot bot = new DiscordBot(database, config);

    }
}
