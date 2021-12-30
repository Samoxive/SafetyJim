use std::collections::HashMap;

use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;

use serenity::prelude::TypeMap;

use crate::config::Config;
use serenity::model::interactions::application_command::ApplicationCommandInteraction;

mod ban;
mod clean;
mod clean_bot;
mod clean_user;
mod hardban;
mod iam;
mod iam_not;
mod info;
mod invite;
mod kick;
mod massban;
mod melo;
mod mute;
mod ping;
mod remind;
mod role_create;
mod role_remove;
mod server;
mod softban;
mod tag;
mod tag_create;
mod tag_edit;
mod tag_list;
mod tag_remove;
mod unban;
mod unmute;
mod warn;
mod whois;

#[async_trait]
pub trait SlashCommand {
    fn command_name(&self) -> &'static str;
    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand;
    async fn handle_command(
        &self,
        context: &Context,
        interaction: &ApplicationCommandInteraction,
        config: &Config,
        services: &TypeMap,
    ) -> anyhow::Result<()>;
}

pub struct SlashCommands(pub HashMap<&'static str, Box<dyn SlashCommand + Send + Sync>>);

pub fn get_all_commands() -> SlashCommands {
    let mut commands_map: HashMap<&'static str, Box<dyn SlashCommand + Send + Sync>> =
        HashMap::new();
    let commands: [Box<dyn SlashCommand + Send + Sync>; 27] = [
        Box::new(ban::BanCommand),
        Box::new(clean::CleanCommand),
        Box::new(clean_bot::CleanBotCommand),
        Box::new(clean_user::CleanUserCommand),
        Box::new(hardban::HardbanCommand),
        Box::new(iam::IAMCommand),
        Box::new(info::InfoCommand),
        Box::new(invite::InviteCommand),
        Box::new(kick::KickCommand),
        Box::new(massban::MassbanCommand),
        Box::new(mute::MuteCommand),
        Box::new(ping::PingCommand),
        Box::new(remind::RemindCommand),
        Box::new(server::ServerCommand),
        Box::new(softban::SoftbanCommand),
        Box::new(tag::TagCommand),
        Box::new(tag_edit::TagEditCommand),
        Box::new(tag_create::TagCreateCommand),
        Box::new(tag_remove::TagRemoveCommand),
        Box::new(tag_list::TagListCommand),
        Box::new(unban::UnbanCommand),
        Box::new(unmute::UnmuteCommand),
        Box::new(warn::WarnCommand),
        Box::new(whois::WhoisCommand),
        Box::new(role_create::RoleCreateCommand),
        Box::new(role_remove::RoleRemoveCommand),
        Box::new(melo::MeloCommand),
    ];

    for command in commands {
        commands_map.insert(command.command_name(), command);
    }

    SlashCommands(commands_map)
}
