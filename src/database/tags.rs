use std::sync::Arc;

use sqlx::{Error, PgPool};

#[derive(sqlx::FromRow)]
pub struct Tag {
    pub id: i32,
    pub guild_id: i64,
    pub name: String,
    pub response: String,
}

pub struct TagsRepository(pub Arc<PgPool>);

impl TagsRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/tags/create_table.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!("sql/tags/create_tags_index_guild_id.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!(
            "sql/tags/create_tags_unique_index_guild_id_name.sql"
        ))
        .execute(&*self.0)
        .await?;

        Ok(())
    }

    pub async fn fetch_guild_tag_by_name(
        &self,
        guild_id: i64,
        name: &str,
    ) -> Result<Option<Tag>, Error> {
        Ok(
            sqlx::query_as::<_, Tag>(include_str!("sql/tags/select_guild_tag_by_name.sql"))
                .bind(guild_id)
                .bind(name)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_tags(&self, guild_id: i64) -> Result<Vec<Tag>, Error> {
        Ok(
            sqlx::query_as::<_, Tag>(include_str!("sql/tags/select_guild_tags.sql"))
                .bind(guild_id)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn insert_tag(&self, tag: Tag) -> Result<(), Error> {
        sqlx::query(include_str!("sql/tags/insert_entity.sql"))
            .bind(tag.guild_id)
            .bind(tag.name)
            .bind(tag.response)
            .execute(&*self.0)
            .await?;

        Ok(())
    }

    pub async fn update_tag(&self, tag: Tag) -> Result<(), Error> {
        sqlx::query(include_str!("sql/tags/update_entity.sql"))
            .bind(tag.id)
            .bind(tag.guild_id)
            .bind(tag.name)
            .bind(tag.response)
            .execute(&*self.0)
            .await?;

        Ok(())
    }

    pub async fn delete_tag(&self, tag_id: i32) -> Result<(), Error> {
        sqlx::query(include_str!("sql/tags/delete_tag.sql"))
            .bind(tag_id)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
