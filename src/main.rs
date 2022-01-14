extern crate serenity;
extern crate typemap_rev;

use std::error::Error;
use std::io;
use std::sync::Arc;

use tracing::Level;
use util::Shutdown;

use crate::config::{get_config, Config};
use crate::flags::Flags;
use crate::service::create_services;
use tracing_subscriber::{filter::LevelFilter, fmt, EnvFilter, Layer};

mod config;
mod constants;
mod database;
mod discord;
mod flags;
mod server;
mod service;
mod util;

use crate::constants::initialize_statics;
use crate::database::setup_database_pool;
use crate::discord::discord_bot::DiscordBot;
use crate::server::run_server;
use tokio::spawn;
use tracing_appender::non_blocking::WorkerGuard;
use tracing_subscriber::layer::SubscriberExt;

fn setup_logging(flags: &Flags) -> WorkerGuard {
    let file_appender = tracing_appender::rolling::daily(&flags.logs_path, "jim.log");
    let (non_blocking_log, guard) = tracing_appender::non_blocking(file_appender);

    let subscriber = tracing_subscriber::registry()
        .with(EnvFilter::from_default_env().add_directive(Level::TRACE.into()))
        .with(
            fmt::Layer::new()
                .json()
                .with_writer(non_blocking_log)
                .with_filter(LevelFilter::INFO),
        )
        .with(
            fmt::Layer::new()
                .with_writer(io::stdout)
                .with_filter(LevelFilter::WARN),
        );

    tracing::subscriber::set_global_default(subscriber).expect("setting default subscriber failed");

    guard
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let flags = Arc::new(argh::from_env::<Flags>());
    let _guard = setup_logging(&flags);

    initialize_statics().await?;
    let config = Arc::new(get_config(&flags.config_path)?);

    let shutdown = Shutdown::new();

    let pool = Arc::new(setup_database_pool(&config).await?);
    let services = Arc::new(create_services(config.clone(), pool.clone()).await?);

    let mut bot = DiscordBot::new(config.clone(), flags, services.clone(), shutdown.clone()).await?;
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
