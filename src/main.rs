extern crate serenity;
extern crate typemap_rev;

use std::error::Error;
use std::io;
use std::sync::Arc;

use tokio::spawn;
use tracing::Level;
use tracing_subscriber::{EnvFilter, filter::LevelFilter, fmt, Layer};
use tracing_subscriber::layer::SubscriberExt;

use mimalloc::MiMalloc;
use util::Shutdown;

use crate::config::{Config, get_config};
use crate::constants::initialize_statics;
use crate::database::setup_database_pool;
use crate::discord::discord_bot::DiscordBot;
use crate::flags::Flags;
use crate::server::run_server;
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

fn setup_logging() {
    let subscriber = tracing_subscriber::registry()
        .with(EnvFilter::from_default_env().add_directive(Level::DEBUG.into()))
        .with(
            fmt::Layer::new()
                .with_writer(io::stdout)
                .with_filter(LevelFilter::TRACE),
        );

    tracing::subscriber::set_global_default(subscriber).expect("setting default subscriber failed");
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let flags = Arc::new(argh::from_env::<Flags>());
    setup_logging();

    initialize_statics().await?;
    let config = Arc::new(get_config()?);

    let shutdown = Shutdown::new();

    let pool = Arc::new(setup_database_pool(&config).await?);
    let services = Arc::new(create_services(config.clone(), pool.clone()).await?);

    let mut bot =
        DiscordBot::new(config.clone(), flags, services.clone(), shutdown.clone()).await?;
    let shard_manager = bot.client.shard_manager.clone();

    let bot_future = spawn(async move { bot.connect().await });

    spawn(async move {
        tokio::signal::ctrl_c().await.unwrap();

        shutdown.shutdown();

        shard_manager.lock().await.shutdown_all().await;
    });

    run_server(config, services).await?;

    let _ = bot_future.await?;

    Ok(())
}
