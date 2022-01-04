package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild

private const val createSQL =
    """
create table if not exists tags (
    id       serial not null primary key,
    guild_id bigint not null,
    name     text not null,
    response text not null
);
"""

private const val insertSQL =
    """
insert into tags (
    guild_id,
    "name",
    response
)
values ($1, $2, $3);
"""

private const val updateSQL =
    """
update tags set
    guild_id = $2,
    "name" = $3,
    response = $4
where id = $1;
"""

object TagsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
        "create index if not exists tags_guild_id_name_unique_index on tags (guild_id);"
    )

    private fun RowSet<Row>.toTagEntities(): List<TagEntity> = this.map {
        TagEntity(
            id = it.getInteger(0),
            guildId = it.getLong(1),
            name = it.getString(2),
            response = it.getString(3)
        )
    }

    suspend fun fetchGuildTags(guild: Guild): List<TagEntity> {
        return pgPool.preparedQueryAwait("select * from tags where guild_id = $1;", Tuple.of(guild.idLong))
            .toTagEntities()
    }

    suspend fun fetchTagByName(guild: Guild, name: String): TagEntity? {
        return pgPool.preparedQueryAwait("select * from tags where guild_id = $1 and name = $2;", Tuple.of(guild.idLong, name))
            .toTagEntities()
            .firstOrNull()
    }

    suspend fun insertTag(tag: TagEntity) {
        pgPool.preparedQueryAwait(insertSQL, tag.toTuple())
    }

    suspend fun updateTag(newTag: TagEntity) {
        pgPool.preparedQueryAwait(updateSQL, newTag.toTupleWithId())
    }

    suspend fun deleteTag(tag: TagEntity) {
        pgPool.preparedQueryAwait("delete from tags where id = $1;", Tuple.of(tag.id))
    }
}

data class TagEntity(
    val id: Int = -1,
    val guildId: Long,
    val name: String,
    val response: String
) {
    fun toTuple(): Tuple {
        return Tuple.of(
            guildId,
            name,
            response
        )
    }

    fun toTupleWithId(): Tuple {
        return Tuple.of(
            id,
            guildId,
            name,
            response
        )
    }
}
