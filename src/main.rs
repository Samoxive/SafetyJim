extern crate safetyjim;

use safetyjim::config;
use std::string::String;
use std::str::FromStr;

fn main() {
    println!("{:?}", config::get_config(String::from_str("config.toml").unwrap()));
}