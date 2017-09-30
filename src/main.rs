extern crate safetyjim;
#[macro_use] extern crate serenity;
extern crate tokio_core;

use safetyjim::config;
use safetyjim::commands::ping::Ping;
use safetyjim::Command;
use safetyjim::Commands;
use std::string::String;
use std::sync::Arc;
use std::marker::Send;
use std::marker::Sync;
use std::collections::HashMap;
use tokio_core::reactor::Handle;
use serenity::model::Message;
use serenity::client::Context;
use serenity::framework::Framework;
fn main() {
    struct MyFramework {
        client:  &'static serenity::Client<JHandler>,
    }

    impl Framework for MyFramework {
        fn dispatch(&mut self, _: Context, msg: Message) {
            let shards = self.client.shards.as_ref();
            let mut x = 0;

            for i in shards.lock().values() {
                x = x + i.lock().guilds_handled()
            }

            msg.channel_id.say(format!("Guild Count: {}", x));
        }
    }
    
    #[derive(Clone)]
    struct JHandler;

    impl serenity::client::EventHandler for JHandler {}

    let config = config::get_config(String::from("config.toml")).unwrap();
    let mut client = serenity::Client::new(config.jim.token.as_str(), JHandler);
    client.with_framework(MyFramework {client: &client});
    client.start_shards(2).unwrap();
    println!("{:?}", config);
}