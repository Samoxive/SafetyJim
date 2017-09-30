#[macro_use] extern crate serde_derive;
#[macro_use] extern crate serenity;
use std::string::String;
pub mod config;

pub trait Command {
    fn usage() -> Vec<String> {
        vec!(String::from("command - default usage text"))
    }
}


#[cfg(test)]
mod tests {
    #[test]
    fn it_works() {
    }
}
