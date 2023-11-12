extern crate serenity;
extern crate typemap_rev;

use std::error::Error;
use std::io;
use std::sync::Arc;

use mimalloc::MiMalloc;
use serenity::all::ApplicationId;
use serenity::http::Http;
use tokio::spawn;
use tracing::{error, Level};
use tracing_subscriber::layer::SubscriberExt;
use tracing_subscriber::{filter::LevelFilter, fmt, EnvFilter, Layer};

use util::Shutdown;

use crate::config::{get_config, Config};
use crate::constants::initialize_statics;
use crate::database::setup_database_pool;
use crate::discord::discord_bot::{initialize_slash_commands, DiscordBot};
use crate::flags::Flags;
// use crate::server::run_server;
use crate::service::create_services;

#[global_allocator]
static GLOBAL: MiMalloc = MiMalloc;

mod config;
mod constants;
mod database;
mod discord;
mod flags;
mod server;
mod service;
mod util;

fn setup_logging() -> Result<(), Box<dyn Error>> {
    let subscriber = tracing_subscriber::registry()
        .with(EnvFilter::from_default_env().add_directive(Level::INFO.into()))
        .with(
            fmt::Layer::new()
                .with_ansi(false)
                .with_writer(io::stdout)
                .with_filter(LevelFilter::INFO),
        );

    tracing::subscriber::set_global_default(subscriber).expect("setting default subscriber failed");

    Ok(())
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let flags = argh::from_env::<Flags>();
    let config = Arc::new(get_config()?);
    setup_logging()?;

    initialize_statics().await?;

    if flags.create_slash_commands {
        let http = Http::new(&config.discord_token);
        http.set_application_id(ApplicationId::new(config.oauth_client_id.parse()?));
        let slash_commands = discord::slash_commands::get_all_commands();
        initialize_slash_commands(&http, &slash_commands)
            .await
            .map_err(|err| {
                error!("failed to create slash commands {}", &err);
                err
            })?;
        return Ok(());
    }

    let shutdown = Shutdown::new();

    let pool = Arc::new(setup_database_pool(&config).await?);
    let services = Arc::new(create_services(config.clone(), pool.clone()).await?);

    let mut bot = DiscordBot::new(config.clone(), services.clone(), shutdown.clone()).await?;
    let shard_manager = bot.client.shard_manager.clone();

    let bot_future = spawn(async move { bot.connect().await });

    let shard_shutdown = shutdown.clone();
    spawn(async move {
        tokio::signal::ctrl_c().await.unwrap();

        shard_shutdown.shutdown();

        shard_manager.shutdown_all().await;
        pool.close().await;
    });

    // run_server(config.clone(), services.clone()).await?;
    server::run_server(config, services, shutdown).await?;

    let _ = bot_future.await?;

    Ok(())
}
