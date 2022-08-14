use anyhow::bail;
use async_trait::async_trait;
use chrono::NaiveDateTime;
use reqwest::{Client, ClientBuilder};
use serde::Deserialize;
use serenity::builder::{
    CreateApplicationCommand, CreateApplicationCommandOption, CreateEmbed, CreateEmbedFooter,
    CreateInteractionResponse, CreateInteractionResponseData,
};
use serenity::client::Context;
use serenity::model::application::command::CommandOptionType;
use serenity::model::application::interaction::application_command::{
    ApplicationCommandInteraction, CommandData,
};
use serenity::model::application::interaction::InteractionResponseType;
use typemap_rev::TypeMap;

use crate::constants::EMBED_COLOR;
use crate::discord::slash_commands::weather::WeatherCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{invisible_failure_reply, verify_guild_slash_command, CommandDataExt};
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

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::default()
            .name("weather")
            .description("gives current weather information for given address")
            .dm_permission(false)
            .add_option(
                CreateApplicationCommandOption::default()
                    .name("address")
                    .description("address for weather location")
                    .kind(CommandOptionType::String)
                    .required(true),
            )
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &ApplicationCommandInteraction,
        config: &Config,
        _services: &TypeMap,
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
                invisible_failure_reply(&*context.http, interaction, "Failed to get address data!")
                    .await;
                return Ok(());
            }
        };

        if geocode_response.status != "OK" {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "Could not detect given address!",
            )
            .await;
            return Ok(());
        }

        let geocode_result = if let Some(result) = geocode_response.results.first() {
            result
        } else {
            invisible_failure_reply(&*context.http, interaction, "Failed to get address data!")
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
                invisible_failure_reply(&*context.http, interaction, "Failed to get weather data!")
                    .await;
                return Ok(());
            }
        };

        let local_time_offset = darksky_response.offset;
        let local_timestamp = now() as i64 + (local_time_offset * 60 * 60) as i64;
        let local_date = NaiveDateTime::from_timestamp(local_timestamp, 0);
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
            .footer(CreateEmbedFooter::default().text(format!("Local Time: {}", local_date_str)))
            .field("Summary", &summary, false)
            .field(
                "Temperature",
                &format!("{} °C / {} °F", temp_c as i32, temp_f as i32),
                true,
            )
            .field("Humidity", &format!("{}%", humidity), true)
            .description(description);

        let data = CreateInteractionResponseData::default().add_embed(embed);

        let response = CreateInteractionResponse::default()
            .kind(InteractionResponseType::ChannelMessageWithSource)
            .interaction_response_data(data);

        let _ = interaction
            .create_interaction_response(&*context.http, response)
            .await;

        Ok(())
    }
}
