use argh::FromArgs;

fn default_config_path() -> String {
    "./config.json".into()
}

fn default_logs_path() -> String {
    "./logs".into()
}

#[derive(FromArgs)]
/// foo
pub struct Flags {
    /// path to config
    #[argh(option, default = "default_config_path()")]
    pub config_path: String,

    /// whether to setup slash commands
    #[argh(switch)]
    pub create_slash_commands: bool,

    /// path to logs directory
    #[argh(option, default = "default_logs_path()")]
    pub logs_path: String,
}
