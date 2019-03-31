package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists membercounts (
    id serial not null primary key,
    guildid bigint not null,
    date bigint not null,
    onlinecount integer not null,
    count integer not null
);
"""

object MemberCountsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
}

data class MemberCountEntity(
        val id: Int = -1,
        val guildId: Long,
        val date: Long,
        val onlineCount: Int,
        val count: Int
)