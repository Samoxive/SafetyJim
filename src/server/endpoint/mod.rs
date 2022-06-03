use std::num::NonZeroU32;

use serde::Deserialize;

pub mod ban;
pub mod captcha;
pub mod hardban;
pub mod kick;
pub mod login;
pub mod mute;
pub mod self_user;
pub mod settings;
pub mod softban;
pub mod warn;

fn default_page() -> NonZeroU32 {
    NonZeroU32::new(1).unwrap()
}

#[derive(Deserialize)]
pub struct ModLogPaginationParams {
    #[serde(default = "default_page")]
    pub page: NonZeroU32,
}
