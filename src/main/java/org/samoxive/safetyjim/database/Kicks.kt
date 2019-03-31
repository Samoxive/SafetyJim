package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists kicklist (
    id serial not null primary key,
    userid bigint not null,
    moderatoruserid bigint not null,
    guildid bigint not null,
    kicktime bigint not null,
    reason text not null
);
"""

object KicksTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
}

data class KickEntity(
        val id: Int = -1,
        val userId: Long,
        val moderatorUserId: Long,
        val guildId: Long,
        val kickTime: Long,
        val reason: String
)