package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple
import net.dv8tion.jda.api.entities.Guild

private const val createSQL = """
create table if not exists taglist (
    id serial not null primary key,
    guildid bigint not null,
    name text not null,
    response text not null
);
"""

private const val insertSQL = """
insert into taglist (
    guildid,
    "name",
    response
)
values ($1, $2, $3);
"""

private const val updateSQL = """
update taglist set
    guildid = $2,
    "name" = $3,
    response = $4
where id = $1;
"""

object TagsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
            "create index if not exists taglist_index_1 on taglist (guildid);"
    )

    private fun PgRowSet.toTagEntities(): List<TagEntity> = this.map {
        TagEntity(
                id = it.getInteger(0),
                guildId = it.getLong(1),
                name = it.getString(2),
                response = it.getString(3)
        )
    }

    suspend fun fetchGuildTags(guild: Guild): List<TagEntity> {
        return pgPool.preparedQueryAwait("select * from taglist where guildid = $1;", Tuple.of(guild.idLong))
                .toTagEntities()
    }

    suspend fun fetchTagByName(guild: Guild, name: String): TagEntity? {
        return pgPool.preparedQueryAwait("select * from taglist where guildid = $1 and name = $2;", Tuple.of(guild.idLong, name))
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
        pgPool.preparedQueryAwait("delete from taglist where id = $1;", Tuple.of(tag.id))
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