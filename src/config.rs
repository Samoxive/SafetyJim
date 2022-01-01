use std::error::Error;
use std::fs::File;
use std::io::Read;

use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct Config {
    pub discord_token: String,
    pub geocode_token: String,
    pub darksky_token: String,
    pub database_user: String,
    pub database_pass: String,
    pub database_host: String,
    pub database_port: u16,
    pub database_name: String,
    pub oauth_client_id: String,
    pub oauth_client_secret: String,
    pub oauth_redirect_uri: String,
    pub self_url: String,
    pub recaptcha_secret: String,
    pub server_secret: String,
    pub server_port: u16,
    pub cors_origin: String,
}

pub fn get_config(config_path: &str) -> Result<Config, Box<dyn Error>> {
    let mut file = File::open(config_path)?;
    let mut contents = String::new();
    file.read_to_string(&mut contents)?;
    Ok(serde_json::from_str::<Config>(&contents)?)
}
