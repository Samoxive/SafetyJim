use crate::util::now;
use anyhow::anyhow;
use once_cell::sync::OnceCell;
use serenity::model::id::UserId;
use serenity::utils::Colour;
use smol_str::SmolStr;
use std::error::Error;

pub const AVATAR_URL: &str =
    "https://cdn.discordapp.com/avatars/313749262687141888/07fd66b4a2aae9c33e719ad6780ad6b0.png";
pub const EMBED_COLOR: Colour = Colour(0x4286F4);
pub const SUPPORT_SERVER_INVITE_LINK: &str = "https://discord.io/safetyjim";
pub const GITHUB_LINK: &str = "https://github.com/samoxive/safetyjim";
pub const INVITE_LINK: &str = "https://discord.com/api/oauth2/authorize?client_id=313749262687141888&permissions=1099918552278&scope=applications.commands%20bot";
pub const JIM_ID_AND_TAG: &str = "Safety Jim#9254 (313749262687141888)";
pub const JIM_ID: UserId = UserId(313749262687141888);
pub const DEFAULT_WORD_FILTER_URL: &str =
    "https://raw.githubusercontent.com/Samoxive/Google-profanity-words/master/list.txt";
pub const DISCORD_API_BASE: &str = "https://discord.com";
pub const DISCORD_CDN_BASE: &str = "https://cdn.discordapp.com";

pub const DEPRECATION_NOTICE: &str = "Due to Discord's changes, the old way of implementing commands as text messages has been deprecated.
Safety Jim migrated to slash commands on 8th of January after much work rewriting most of the project to adapt to changes.
For this migration there is no action needed, however if you invited Jim after 24th of March, 2021 you need to kick him and invite back for slash commands to appear in your server.

To report potential bugs and feedback, feel free to head to the support Discord server.";

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
