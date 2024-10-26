use anyhow::{anyhow, bail};
use async_trait::async_trait;
use chrono::DateTime;
use reqwest::{Client, ClientBuilder};
use serde::Deserialize;
use serenity::all::Context;
use serenity::all::{
    CommandData, CommandInteraction, CommandOptionType, CommandType, InstallationContext,
    InteractionContext,
};
use serenity::builder::{CreateCommand, CreateCommandOption, CreateEmbed, CreateEmbedFooter};

use crate::constants::EMBED_COLOR;
use crate::discord::slash_commands::weather::WeatherCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_embed, reply_to_interaction_str, verify_guild_slash_command,
    CommandDataExt,
};
use crate::service::Services;
use crate::util::now;
use crate::Config;

const GEOCODE_URL: &str = "https://maps.googleapis.com/maps/api/geocode/json";
const DARKSKY_URL: &str = "https://api.darksky.net/forecast";

#[derive(Deserialize)]
struct GeocodeResponse {
    status: String,
    results: Vec<GeocodeResponseResult>,
}

#[derive(Deserialize)]
struct GeocodeResponseResult {
    formatted_address: String,
    geometry: GeocodeResponseGeometry,
}

#[derive(Deserialize)]
struct GeocodeResponseGeometry {
    location: GeocodeResponseLocation,
}

#[derive(Deserialize)]
struct GeocodeResponseLocation {
    lat: f32,
    lng: f32,
}

#[derive(Deserialize)]
pub struct DarkskyResponse {
    offset: i32,
    currently: DarkskyResponseCurrently,
}

#[derive(Deserialize)]
pub struct DarkskyResponseCurrently {
    summary: String,
    temperature: f32,
    humidity: f32,
    icon: String,
}

pub struct WeatherCommand {
    client: Client,
}

impl WeatherCommand {
    pub fn new() -> WeatherCommand {
        WeatherCommand {
            client: ClientBuilder::new()
                .user_agent("Safety Jim")
                .build()
                .expect("constructing client failed"),
        }
    }
}

struct WeatherCommandOptions<'a> {
    address: &'a str,
}

enum WeatherCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &CommandData,
) -> Result<WeatherCommandOptions, WeatherCommandOptionFailure> {
    let address = if let Some(s) = data.string("address") {
        s
    } else {
        return Err(MissingOption);
    };

    Ok(WeatherCommandOptions { address })
}

#[async_trait]
impl SlashCommand for WeatherCommand {
    fn command_name(&self) -> &'static str {
        "weather"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("weather")
            .kind(CommandType::ChatInput)
            .description("gives current weather information for given address")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "address",
                    "address for weather location",
                )
                .required(true),
            )
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &CommandInteraction,
        config: &Config,
        _services: &Services,
    ) -> anyhow::Result<()> {
        let _ = verify_guild_slash_command(interaction)?;

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let geocode_response = match self
            .client
            .get(GEOCODE_URL)
            .query(&[
                ("key", config.geocode_token.as_str()),
                ("address", options.address),
            ])
            .send()
            .await?
            .json::<GeocodeResponse>()
            .await
        {
            Ok(response) => response,
            Err(_err) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Failed to get address data!",
                    true,
                )
                .await;
                return Ok(());
            }
        };

        if geocode_response.status != "OK" {
            reply_to_interaction_str(
                &context.http,
                interaction,
                "Could not detect given address!",
                true,
            )
            .await;
            return Ok(());
        }

        let geocode_result = if let Some(result) = geocode_response.results.first() {
            result
        } else {
            reply_to_interaction_str(
                &context.http,
                interaction,
                "Failed to get address data!",
                true,
            )
            .await;
            return Ok(());
        };

        let formatted_address = &geocode_result.formatted_address;
        let lat = geocode_result.geometry.location.lat;
        let lng = geocode_result.geometry.location.lng;

        let darksky_response = match self
            .client
            .get(format!(
                "{}/{}/{},{}",
                DARKSKY_URL, config.darksky_token, lat, lng
            ))
            .send()
            .await?
            .json::<DarkskyResponse>()
            .await
        {
            Ok(response) => response,
            Err(_) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Failed to get weather data!",
                    true,
                )
                .await;
                return Ok(());
            }
        };

        let local_time_offset = darksky_response.offset;
        let local_timestamp = now() as i64 + (local_time_offset * 60 * 60) as i64;
        let local_date = DateTime::from_timestamp(local_timestamp, 0)
            .ok_or_else(|| anyhow!("invalid timestamp from darksky"))?;
        let local_date_formatted = local_date.format("%a, %d %b %Y %H:%M:%S GMT ");
        let local_date_str = if local_time_offset > 0 {
            format!("{} +{}", local_date_formatted, local_time_offset)
        } else {
            format!("{} {}", local_date_formatted, local_time_offset)
        };

        let summary = darksky_response.currently.summary;
        let temp_f = darksky_response.currently.temperature;
        let temp_c = ((temp_f - 32.) * 5.) / 9.;
        let humidity = (darksky_response.currently.humidity * 100.) as i32;
        let icon = darksky_response.currently.icon;

        let description = match icon.as_str() {
            "clear-day" => ":sunny:",
            "clear-night" => ":crescent_moon:",
            "rain" => ":cloud_rain:",
            "snow" => ":cloud_snow:",
            "sleet" => ":cloud_snow:",
            "partly-cloudy-day" => ":partly_sunny:",
            "partly-cloudy-night" => ":partly_sunny:",
            "fog" => ":fog:",
            "cloudy" => ":cloud:",
            "wind" => ":wind_blowing_face:",
            _ => "<unknown>",
        };

        let embed = CreateEmbed::default()
            .colour(EMBED_COLOR)
            .title(format!("Weather in {}", formatted_address))
            .footer(CreateEmbedFooter::new(format!(
                "Local Time: {}",
                local_date_str
            )))
            .field("Summary", &summary, false)
            .field(
                "Temperature",
                format!("{} °C / {} °F", temp_c as i32, temp_f as i32),
                true,
            )
            .field("Humidity", format!("{}%", humidity), true)
            .description(description);

        reply_to_interaction_embed(&context.http, interaction, embed, false).await;

        Ok(())
    }
}
