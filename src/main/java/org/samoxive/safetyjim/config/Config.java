package org.samoxive.safetyjim.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Config {
    public String version;
    public Jim jim;
    public Database database;
    public Metrics metrics;
    public BotList botlist;
    public Oauth oauth;
    public Server server;

    public static Config fromFileName(String filename) throws IOException {
        String fileContent = new String(Files.readAllBytes(Paths.get(filename)));
        Toml toml = (new Toml()).read(fileContent);
        return toml.to(Config.class);
    }

    public class Jim {
        public String token;
        public String default_prefix;
        public Integer shard_count;
    }

    public class Database {
        public String user;
        public String pass;
        public String host;
        public String name;
        public Integer port;
    }

    public class Metrics {
        public Boolean enabled;
        public String api_key;
        public String host;
        public Integer flush_interval;
    }

    public class BotList {
        public Boolean enabled;
        public List<list> list;
    }

    public class list {
        public String name;
        public String url;
        public String token;
        public boolean ignore_errors;
    }

    public class Oauth {
        public String client_id;
        public String client_secret;
        public String redirect_uri;
    }

    public class Server {
        public String secret;
        public String base_url;
        public int port;
    }
}
