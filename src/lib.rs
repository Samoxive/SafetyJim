#[macro_use] extern crate serde_derive;
#[macro_use] extern crate serenity;
use std::string::String;
use std::sync::Arc;
use std::collections::HashMap;
pub mod config;
pub mod commands;

pub trait Command {
    fn usage(&self) -> Vec<String> {
        vec!(String::from("command - default usage text"))
    }
}

pub type Commands = HashMap<String, Arc<Command>>;


#[cfg(test)]
mod tests {
    #[test]
    fn it_works() {
    }
}
