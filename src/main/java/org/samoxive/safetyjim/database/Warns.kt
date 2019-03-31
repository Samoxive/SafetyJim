package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists warnlist (
    id serial not null primary key,
    userid bigint not null,
    moderatoruserid bigint not null,
    guildid bigint not null,
    warntime bigint not null,
    reason text not null
);
"""

object WarnsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
}

data class WarnEntity(
        val id: Int = -1,
        val userId: Long,
        val moderatorUserId: Long,
        val guildId: Long,
        val warnTime: Long,
        val reason: String
)