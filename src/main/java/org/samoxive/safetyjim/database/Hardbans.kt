package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists hardbanlist (
    id serial not null primary key,
    userid bigint not null,
    moderatoruserid bigint not null,
    guildid bigint not null,
    hardbantime bigint not null,
    reason text not null
);
"""

object HardbansTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
}

data class HardbanEntity(
        val id: Int = -1,
        val userId: Long,
        val moderatorUserId: Long,
        val guildId: Long,
        val hardbanTime: Long,
        val reason: String
)