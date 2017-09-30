extern crate toml;
use std::fs::File;
use std::io::prelude::*;

#[derive(Deserialize, Serialize, Debug)]
pub struct Config {
    jim: Jim,
    database: Database,
    metrics: Metrics,
    botlist: BotLists,
}

#[derive(Deserialize, Serialize, Debug)]
struct BotLists {
        enabled: bool,
        list: Vec<List>,
}

#[derive(Deserialize, Serialize, Debug)]
struct Metrics {
        enabled: bool,
        api_key: String,
        host: String,
        flush_interval: i32,
}

#[derive(Deserialize, Serialize, Debug)]
struct Database {
        user: String,
        pass: String,
        host: String,
        name: String,
        port: i32,
}

#[derive(Deserialize, Serialize, Debug)]
struct Jim {
        token: String,
        default_prefix: String,
        shard_count: i32, 
}

#[derive(Deserialize, Serialize, Debug)]
struct List {
    name: String,
    url: String,
    token: String,
    ignore_errors: bool,
}

pub fn get_config(path: String) -> Config {
    // TODO(sam): FIX THE UNWRAPS
    let mut config_file = File::open(path).unwrap();
    let mut config_read = String::new();
    config_file.read_to_string(&mut config_read).unwrap();

    let config: Config = toml::from_str(config_read.as_str()).unwrap();

    config
}