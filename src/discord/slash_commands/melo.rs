use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::application::interaction::application_command::ApplicationCommandInteraction;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::reply_with_str;

pub struct MeloCommand;

#[async_trait]
impl SlashCommand for MeloCommand {
    fn command_name(&self) -> &'static str {
        "melo"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("melo")
            .description("melo.")
            .dm_permission(true)
            .default_member_permissions(Permissions::all())
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &ApplicationCommandInteraction,
        _config: &Config,
        _services: &TypeMap,
    ) -> anyhow::Result<()> {
        reply_with_str(&context.http, interaction, ":melon:").await;

        Ok(())
    }
}
