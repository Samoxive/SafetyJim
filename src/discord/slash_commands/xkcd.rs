use anyhow::bail;
use async_trait::async_trait;
use regex::Regex;
use reqwest::{Client, ClientBuilder};
use scraper::{Html, Selector};
use serenity::all::Context;
use serenity::all::{
    CommandData, CommandInteraction, CommandOptionType, CommandType, InstallationContext,
    InteractionContext,
};
use serenity::builder::{CreateCommand, CreateCommandOption};

use crate::discord::slash_commands::xkcd::XkcdCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{reply_to_interaction_str, verify_guild_slash_command, CommandDataExt};
use crate::service::Services;
use crate::Config;

const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36";
const DDG_URL: &str = "https://duckduckgo.com/html/";
const XKCD_URL_PATTERN: &str = r"^https?://(www\.)?xkcd\.com/\d+/$";

pub struct XkcdCommand {
    client: Client,
}

impl XkcdCommand {
    pub fn new() -> XkcdCommand {
        XkcdCommand {
            client: ClientBuilder::new()
                .user_agent(USER_AGENT)
                .build()
                .expect("constructing client failed"),
        }
    }
}

struct XkcdCommandOptions<'a> {
    description: &'a str,
}

enum XkcdCommandOptionFailure {
    MissingOption,
}

fn generate_options(data: &CommandData) -> Result<XkcdCommandOptions, XkcdCommandOptionFailure> {
    let description = if let Some(s) = data.string("description") {
        s
    } else {
        return Err(MissingOption);
    };

    Ok(XkcdCommandOptions { description })
}

fn parse_ddg_response(response: &str) -> Option<String> {
    let href_regex = Regex::new(XKCD_URL_PATTERN).ok()?;
    let selector = Selector::parse(".result__url").ok()?;
    let document = Html::parse_document(response);

    document
        .select(&selector)
        .find(|element| {
            let href = match element.value().attr("href") {
                Some(href) => href,
                None => return false,
            };

            href_regex.is_match(href)
        })
        .and_then(|element| element.value().attr("href").map(|href| href.to_string()))
}

#[async_trait]
impl SlashCommand for XkcdCommand {
    fn command_name(&self) -> &'static str {
        "xkcd"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("xkcd")
            .kind(CommandType::ChatInput)
            .description("searches xkcd comics with given description or partial title")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "description",
                    "description or partial title of the comic",
                )
                .required(true),
            )
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &CommandInteraction,
        _config: &Config,
        _services: &Services,
    ) -> anyhow::Result<()> {
        let _ = verify_guild_slash_command(interaction)?;

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let response = match self
            .client
            .get(DDG_URL)
            .query(&[("q", &format!("{} xkcd", options.description))])
            .send()
            .await?
            .text()
            .await
        {
            Ok(response) => response,
            Err(_) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Failed to search for xkcd comic!",
                    true,
                )
                .await;
                return Ok(());
            }
        };

        let link_href = parse_ddg_response(&response);

        if let Some(link) = link_href {
            let _ = reply_to_interaction_str(&context.http, interaction, &link, false).await;
        } else {
            reply_to_interaction_str(
                &context.http,
                interaction,
                "Failed to find a relevant xkcd comic! Shocking, I know.",
                true,
            )
            .await;
        }

        Ok(())
    }
}
