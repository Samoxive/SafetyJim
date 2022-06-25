use serenity::model::id::{GuildId, RoleId};
use tracing::error;
use typemap_rev::TypeMapKey;

use crate::database::iam_roles::{IAMRole, IAMRolesRepository};

impl TypeMapKey for IAMRoleService {
    type Value = IAMRoleService;
}

pub enum InsertIAMRoleFailure {
    RoleExists,
    Unknown,
}

pub enum RemoveIAMRoleFailure {
    RoleDoesNotExist,
    Unknown,
}

pub struct IAMRoleService {
    pub repository: IAMRolesRepository,
}

impl IAMRoleService {
    pub async fn is_iam_role(&self, guild_id: GuildId, role_id: RoleId) -> bool {
        self.fetch_guild_iam_role(guild_id, role_id).await.is_some()
    }

    pub async fn fetch_guild_iam_role(
        &self,
        guild_id: GuildId,
        role_id: RoleId,
    ) -> Option<IAMRole> {
        self.repository
            .fetch_guild_iam_role(guild_id.0.get() as i64, role_id.0.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch guild iam role {:?}", err);
                err
            })
            .ok()
            .flatten()
    }

    pub async fn _fetch_guild_iam_roles(&self, guild_id: GuildId) -> Vec<IAMRole> {
        self.repository
            ._fetch_guild_iam_roles(guild_id.0.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch guild iam roles {:?}", err);
                err
            })
            .ok()
            .unwrap_or_default()
    }

    pub async fn insert_iam_role(
        &self,
        guild_id: GuildId,
        role_id: RoleId,
    ) -> Result<(), InsertIAMRoleFailure> {
        let role = IAMRole {
            id: 0,
            guild_id: guild_id.0.get() as i64,
            role_id: role_id.0.get() as i64,
        };

        match self.repository.insert_iam_role(role).await {
            Ok(_) => Ok(()),
            Err(sqlx::Error::Database(_)) => Err(InsertIAMRoleFailure::RoleExists),
            Err(err) => {
                error!("failed to insert iam role {:?}", err);
                Err(InsertIAMRoleFailure::Unknown)
            }
        }
    }

    pub async fn delete_iam_role(
        &self,
        guild_id: GuildId,
        role_id: RoleId,
    ) -> Result<(), RemoveIAMRoleFailure> {
        let role = match self
            .repository
            .fetch_guild_iam_role(guild_id.0.get() as i64, role_id.0.get() as i64)
            .await
        {
            Ok(Some(role)) => role,
            Ok(None) => return Err(RemoveIAMRoleFailure::RoleDoesNotExist),
            Err(err) => {
                error!("failed to fetch iam role {:?}", err);
                return Err(RemoveIAMRoleFailure::Unknown);
            }
        };

        match self.repository.delete_iam_role(role.id).await {
            Ok(_) => Ok(()),
            Err(err) => {
                error!("failed to delete iam role! {:?}", err);
                Err(RemoveIAMRoleFailure::Unknown)
            }
        }
    }
}
