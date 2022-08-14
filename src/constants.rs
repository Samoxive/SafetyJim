use std::error::Error;
use std::num::NonZeroU64;

use anyhow::anyhow;
use once_cell::sync::OnceCell;
use serenity::model::colour::Colour;
use serenity::model::id::UserId;
use smol_str::SmolStr;

use crate::util::now;

pub const AVATAR_URL: &str =
    "https://cdn.discordapp.com/avatars/313749262687141888/07fd66b4a2aae9c33e719ad6780ad6b0.png";
pub const EMBED_COLOR: Colour = Colour(0x4286F4);
pub const SUPPORT_SERVER_INVITE_LINK: &str = "https://discord.io/safetyjim";
pub const GITHUB_LINK: &str = "https://github.com/samoxive/safetyjim";
pub const INVITE_LINK: &str = "https://discord.com/api/oauth2/authorize?client_id=313749262687141888&permissions=1099918552278&scope=applications.commands%20bot";
pub const JIM_ID_AND_TAG: &str = "Safety Jim#9254 (313749262687141888)";
pub const JIM_ID: UserId = UserId(unsafe { NonZeroU64::new_unchecked(313749262687141888u64) });
pub const DEFAULT_WORD_FILTER_URL: &str =
    "https://raw.githubusercontent.com/Samoxive/Google-profanity-words/master/list.txt";
pub const DISCORD_API_BASE: &str = "https://discord.com";
pub const DISCORD_CDN_BASE: &str = "https://cdn.discordapp.com";

pub static START_EPOCH: OnceCell<u64> = OnceCell::new();
pub static DEFAULT_BLOCKED_WORDS: OnceCell<Vec<SmolStr>> = OnceCell::new();

pub async fn initialize_statics() -> Result<(), Box<dyn Error>> {
    START_EPOCH
        .set(now())
        .map_err(|_| anyhow!("failed to set start epoch!"))?;

    let filter_body = reqwest::get(DEFAULT_WORD_FILTER_URL).await?.text().await?;

    DEFAULT_BLOCKED_WORDS
        .set(
            filter_body
                .split('\n')
                .map(|word| word.trim())
                .map(SmolStr::new)
                .filter(|word| !word.is_empty())
                .collect(),
        )
        .map_err(|_| anyhow!("failed to set default blocked words!"))?;

    Ok(())
}
