package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists softbanlist (
    id serial not null primary key,
    userid bigint not null,
    moderatoruserid bigint not null,
    guildid bigint not null,
    softbantime bigint not null,
    deletedays integer not null,
    reason text not null
);
"""

object SoftbansTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
}

data class SoftbanEntity(
        val id: Int = -1,
        val userId: Long,
        val moderatorUserId: Long,
        val guildId: Long,
        val softbanTime: Long,
        val deleteDays: Int,
        val reason: String
)