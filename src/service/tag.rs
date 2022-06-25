use serenity::model::id::GuildId;
use tracing::error;
use typemap_rev::TypeMapKey;

use crate::database::tags::{Tag, TagsRepository};

const TAG_CONTENT_SIZE_LIMIT: usize = 2000;

impl TypeMapKey for TagService {
    type Value = TagService;
}

pub enum InsertTagFailure {
    TagExists,
    ContentTooBig,
    Unknown,
}

pub enum UpdateTagFailure {
    TagDoesNotExist,
    ContentTooBig,
    Unknown,
}

pub enum RemoveTagFailure {
    TagDoesNotExist,
    Unknown,
}

pub struct TagService {
    pub repository: TagsRepository,
}

impl TagService {
    pub async fn get_tag_content(&self, guild_id: GuildId, name: &str) -> Option<String> {
        self.repository
            .fetch_guild_tag_by_name(guild_id.0.get() as i64, name)
            .await
            .map_err(|err| {
                error!("failed to fetch guild tag by name {:?}", err);
                err
            })
            .ok()
            .flatten()
            .map(|tag| tag.response)
    }

    pub async fn insert_tag(
        &self,
        guild_id: GuildId,
        name: &str,
        content: &str,
    ) -> Result<(), InsertTagFailure> {
        if content.len() > TAG_CONTENT_SIZE_LIMIT {
            return Err(InsertTagFailure::ContentTooBig);
        }

        let new_tag = Tag {
            id: 0,
            guild_id: guild_id.0.get() as i64,
            name: name.into(),
            response: content.into(),
        };

        let result = self.repository.insert_tag(new_tag).await;
        match result {
            Ok(_) => Ok(()),
            Err(sqlx::Error::Database(_)) => Err(InsertTagFailure::TagExists),
            Err(err) => {
                error!("failed to insert tag {:?}", err);
                Err(InsertTagFailure::Unknown)
            }
        }
    }

    pub async fn update_tag(
        &self,
        guild_id: GuildId,
        name: &str,
        new_content: &str,
    ) -> Result<(), UpdateTagFailure> {
        if new_content.len() > TAG_CONTENT_SIZE_LIMIT {
            return Err(UpdateTagFailure::ContentTooBig);
        }

        let mut tag = match self
            .repository
            .fetch_guild_tag_by_name(guild_id.0.get() as i64, name)
            .await
        {
            Ok(Some(tag)) => tag,
            Ok(None) => return Err(UpdateTagFailure::TagDoesNotExist),
            Err(err) => {
                error!("failed to fetch tag by name {:?}", err);
                return Err(UpdateTagFailure::Unknown);
            }
        };

        tag.response = new_content.into();

        let result = self.repository.update_tag(tag).await;
        match result {
            Ok(_) => Ok(()),
            Err(err) => {
                error!("failed to update tag {:?}", err);
                Err(UpdateTagFailure::Unknown)
            }
        }
    }

    pub async fn remove_tag(&self, guild_id: GuildId, name: &str) -> Result<(), RemoveTagFailure> {
        let tag = match self
            .repository
            .fetch_guild_tag_by_name(guild_id.0.get() as i64, name)
            .await
        {
            Ok(Some(tag)) => tag,
            Ok(None) => return Err(RemoveTagFailure::TagDoesNotExist),
            Err(err) => {
                error!("failed to fetch tag by name {:?}", err);
                return Err(RemoveTagFailure::Unknown);
            }
        };

        match self.repository.delete_tag(tag.id).await {
            Ok(_) => Ok(()),
            Err(err) => {
                error!("failed to delete tag {:?}", err);
                Err(RemoveTagFailure::Unknown)
            }
        }
    }

    pub async fn get_tag_names(&self, guild_id: GuildId) -> Vec<String> {
        match self.repository.fetch_guild_tags(guild_id.0.get() as i64).await {
            Ok(tags) => tags.into_iter().map(|tag| tag.name).collect(),
            Err(err) => {
                error!("failed to fetch guild tags {:?}", err);
                vec![]
            }
        }
    }
}
