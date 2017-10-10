package org.samoxive.safetyjim;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.samoxive.safetyjim.config.Config;

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
        }
}
