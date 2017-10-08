package org.samoxive.safetyjim.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Config {
    public Jim jim;
    public Database database;
    public Metrics metrics;
    public BotList botlist;

    public static Config fromFileName(String filename) throws IOException {
        try {
            //
            String fileContent = new String(Files.readAllBytes(Paths.get(filename)));

            Toml toml = (new Toml()).read(fileContent);
            return toml.to(Config.class);
        } catch (IOException e) {
            throw e;
        }
    }

    private class Jim {
        public String token;
        public String default_prefix;
        public Integer shard_count;
    }

    private class Database {
        public String user;
        public String pass;
        public String host;
        public String name;
        public Integer port;
    }

    private class Metrics {
        public Boolean enabled;
        public String api_key;
        public String host;
        public Integer flush_interval;
    }

    private class BotList {
        public Boolean enabled;
        public List<list> list;
    }

    private class list {
        public String name;
        public String url;
        public String token;
        public boolean ignore_errors;
    }
}
