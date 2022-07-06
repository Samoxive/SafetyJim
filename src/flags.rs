use argh::FromArgs;

#[derive(FromArgs)]
/// foo
pub struct Flags {
    /// whether to setup slash commands
    #[argh(switch)]
    pub create_slash_commands: bool,
}
