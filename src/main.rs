extern crate safetyjim;
#[macro_use] extern crate serenity;

use safetyjim::config;
use std::string::String;
use std::sync::Arc;

fn main() {
    struct JHandler;
    impl serenity::client::EventHandler for JHandler {}
    let config = config::get_config(String::from("config.toml")).unwrap();
    let mut client = serenity::Client::new(config.jim.token.as_str(), JHandler);
    client.start_shards(2).unwrap();
    println!("{:?}", config);
}