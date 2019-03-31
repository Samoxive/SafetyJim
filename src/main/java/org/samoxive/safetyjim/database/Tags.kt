package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists taglist (
    id serial not null primary key,
    guildid bigint not null,
    name text not null,
    response text not null
);
"""

object TagsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
            "create unique index if not exists taglist_index_1 on taglist (guildid, name);"
    )
}

data class TagEntity(
        val id: Int = -1,
        val guildId: Long,
        val name: String,
        val response: String
)