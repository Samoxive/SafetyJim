use std::sync::Arc;

use sqlx::PgPool;
use typemap_rev::TypeMap;

use ban::BanService;
use hardban::HardbanService;
use iam_role::IAMRoleService;
use invalid_uuid::InvalidUUIDService;
use join::JoinService;
use kick::KickService;
use mute::MuteService;
use reminder::ReminderService;
use setting::SettingService;
use softban::SoftbanService;
use tag::TagService;
use user_secret::UserSecretService;
use warn::WarnService;

use crate::database::bans::BansRepository;
use crate::database::hardbans::HardbansRepository;
use crate::database::iam_roles::IAMRolesRepository;
use crate::database::invalid_uuids::InvalidUUIDsRepository;
use crate::database::joins::JoinsRepository;
use crate::database::kicks::KicksRepository;
use crate::database::mutes::MutesRepository;
use crate::database::reminders::RemindersRepository;
use crate::database::settings::SettingsRepository;
use crate::database::softbans::SoftbansRepository;
use crate::database::tags::TagsRepository;
use crate::database::user_secrets::UserSecretsRepository;
use crate::database::warns::WarnsRepository;
use crate::service::guild::GuildService;
use crate::service::guild_statistic::GuildStatisticService;
use crate::service::shard_statistic::ShardStatisticService;
use crate::Config;
use crate::service::watchdog::WatchdogService;

pub mod ban;
pub mod guild;
pub mod guild_statistic;
pub mod hardban;
pub mod iam_role;
pub mod invalid_uuid;
pub mod join;
pub mod kick;
pub mod mute;
pub mod reminder;
pub mod setting;
pub mod shard_statistic;
pub mod softban;
pub mod tag;
pub mod user_secret;
pub mod warn;
pub mod watchdog;

pub type Services = TypeMap;

pub async fn create_services(config: Arc<Config>, pool: Arc<PgPool>) -> anyhow::Result<Services> {
    let bans_repository = BansRepository(pool.clone());
    let hardbans_repository = HardbansRepository(pool.clone());
    let iam_roles_repository = IAMRolesRepository(pool.clone());
    let invalid_uuids_repository = InvalidUUIDsRepository(pool.clone());
    let joins_repository = JoinsRepository(pool.clone());
    let kicks_repository = KicksRepository(pool.clone());
    let mutes_repository = MutesRepository(pool.clone());
    let reminders_repository = RemindersRepository(pool.clone());
    let settings_repository = SettingsRepository(pool.clone());
    let softbans_repository = SoftbansRepository(pool.clone());
    let tags_repository = TagsRepository(pool.clone());
    let user_secrets_repository = UserSecretsRepository(pool.clone());
    let warns_repository = WarnsRepository(pool.clone());

    bans_repository.initialize().await?;
    hardbans_repository.initialize().await?;
    iam_roles_repository.initialize().await?;
    invalid_uuids_repository.initialize().await?;
    joins_repository.initialize().await?;
    kicks_repository.initialize().await?;
    mutes_repository.initialize().await?;
    reminders_repository.initialize().await?;
    settings_repository.initialize().await?;
    softbans_repository.initialize().await?;
    tags_repository.initialize().await?;
    user_secrets_repository.initialize().await?;
    warns_repository.initialize().await?;

    let ban_service = BanService {
        repository: bans_repository,
    };
    let hardban_service = HardbanService {
        repository: hardbans_repository,
    };
    let iam_role_service = IAMRoleService {
        repository: iam_roles_repository,
    };
    let invalid_uuid_service = InvalidUUIDService {
        repository: invalid_uuids_repository,
    };
    let join_service = JoinService {
        repository: joins_repository,
    };
    let kick_service = KickService {
        repository: kicks_repository,
    };
    let mute_service = MuteService {
        repository: mutes_repository,
    };
    let reminder_service = ReminderService {
        repository: reminders_repository,
    };
    let setting_service = SettingService::new(settings_repository);
    let softban_service = SoftbanService {
        repository: softbans_repository,
    };
    let tags_service = TagService {
        repository: tags_repository,
    };
    let user_secrets_service = UserSecretService::new(config, user_secrets_repository);
    let warns_service = WarnService {
        repository: warns_repository,
    };
    let guild_statistic_service = GuildStatisticService::new();
    let shard_statistic_service = ShardStatisticService::new();
    let guild_service = GuildService::new();
    let watchdog_service = WatchdogService::new();

    let mut services = Services::new();
    services.insert::<BanService>(ban_service);
    services.insert::<HardbanService>(hardban_service);
    services.insert::<IAMRoleService>(iam_role_service);
    services.insert::<InvalidUUIDService>(invalid_uuid_service);
    services.insert::<JoinService>(join_service);
    services.insert::<KickService>(kick_service);
    services.insert::<MuteService>(mute_service);
    services.insert::<ReminderService>(reminder_service);
    services.insert::<SettingService>(setting_service);
    services.insert::<SoftbanService>(softban_service);
    services.insert::<TagService>(tags_service);
    services.insert::<UserSecretService>(user_secrets_service);
    services.insert::<WarnService>(warns_service);
    services.insert::<GuildStatisticService>(guild_statistic_service);
    services.insert::<ShardStatisticService>(shard_statistic_service);
    services.insert::<GuildService>(guild_service);
    services.insert::<WatchdogService>(watchdog_service);

    Ok(services)
}
