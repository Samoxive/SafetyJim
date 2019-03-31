package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists joinlist (
    id serial not null primary key,
    userid bigint not null,
    guildid bigint not null,
    jointime bigint not null,
    allowtime bigint not null,
    allowed boolean not null
);
"""

object JoinsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
}

data class JoinEntity(
        val id: Int = -1,
        val userId: Long,
        val guildId: Long,
        val joinTime: Long,
        val allowTime: Long,
        val allowed: Boolean
)