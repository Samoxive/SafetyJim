extern crate safetyjim;
#[macro_use] extern crate serenity;

use safetyjim::config;
use safetyjim::commands::ping::Ping;
use safetyjim::Command;
use safetyjim::Commands;
use std::string::String;
use std::sync::Arc;
use std::marker::Send;
use std::marker::Sync;
use std::collections::HashMap;

fn main() {
    struct JHandler {
        commands: Arc<Commands>,
    }

    unsafe impl Sync for JHandler {}
    unsafe impl Send for JHandler {}

    impl serenity::client::EventHandler for JHandler {
        fn on_message(&self, ctx: serenity::client::Context, msg: serenity::model::Message) {
            if msg.author.bot {
                return;
            }
            let x: &Command = self.commands.get("ping").unwrap().as_ref();
            let y: &String = &x.usage()[0];
            msg.channel_id.say((*y).as_str());
        } 
    }
    let config = config::get_config(String::from("config.toml")).unwrap();
    let mut commands: Commands = HashMap::new();
    commands.insert(String::from("ping"), Arc::new(Ping));
    let mut client = serenity::Client::new(config.jim.token.as_str(), JHandler { commands: Arc::new(commands) });
    client.start_shards(2).unwrap();
    println!("{:?}", config);
}