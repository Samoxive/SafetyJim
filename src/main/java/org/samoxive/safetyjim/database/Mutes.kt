package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists mutelist (
    id serial not null primary key,
    userid bigint not null,
    moderatoruserid bigint not null,
    guildid bigint not null,
    mutetime bigint not null,
    expiretime bigint,
    reason text not null,
    expires boolean not null,
    unmuted boolean not null
);
"""

object MutesTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
}

data class MuteEntity(
        val id: Int = -1,
        val userId: Long,
        val moderatorUserId: Long,
        val guildId: Long,
        val muteTime: Long,
        val expireTime: Long?,
        val reason: String,
        val expires: Boolean,
        val unmuted: Boolean
)