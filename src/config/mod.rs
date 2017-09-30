extern crate toml;
use std::fs::File;
use std::io::prelude::*;
use std::error::Error;

#[derive(Deserialize, Serialize, Debug)]
pub struct Config {
    pub jim: Jim,
    pub database: Database,
    pub metrics: Metrics,
    pub botlist: BotLists,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct BotLists {
    pub enabled: bool,
    pub list: Vec<List>,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct Metrics {
    pub enabled: bool,
    pub api_key: String,
    pub host: String,
    pub flush_interval: i32,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct Database {
    pub user: String,
    pub pass: String,
    pub host: String,
    pub name: String,
    pub port: i32,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct Jim {
    pub token: String,
    pub default_prefix: String,
    pub shard_count: i32, 
}

#[derive(Deserialize, Serialize, Debug)]
pub struct List {
    pub name: String,
    pub url: String,
    pub token: String,
    pub ignore_errors: bool,
}

pub fn get_config(path: String) -> Result<Config, Box<Error>> {
    let mut config_file = File::open(path)?;
    let mut config_read = String::new();
    config_file.read_to_string(&mut config_read)?;

    let config: Config = toml::from_str(config_read.as_str())?;

    Ok(config)
}