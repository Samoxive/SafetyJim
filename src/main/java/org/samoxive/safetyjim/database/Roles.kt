package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists rolelist (
    id serial not null primary key,
    guildid bigint not null,
    roleid bigint not null
);
"""

object RolesTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
            "create unique index if not exists rolelist_index_1 on rolelist (guildid, roleid);"
    )
}

data class RoleEntity(
        val id: Int = -1,
        val guildId: Long,
        val roleId: Long
)