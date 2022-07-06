use serde::Deserialize;

#[derive(Debug, Deserialize)]
pub struct Config {
    pub discord_token: String,
    pub geocode_token: String,
    pub darksky_token: String,
    pub database_url: String,
    pub oauth_client_id: String,
    pub oauth_client_secret: String,
    pub oauth_redirect_uri: String,
    pub self_url: String,
    pub recaptcha_secret: String,
    pub server_secret: String,
    pub server_port: u16,
    pub cors_origin: String,
}

pub fn get_config() -> envy::Result<Config> {
    envy::from_env()
}
