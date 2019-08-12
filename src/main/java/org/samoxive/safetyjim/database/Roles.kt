package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role

private const val createSQL = """
create table if not exists rolelist (
    id serial not null primary key,
    guildid bigint not null,
    roleid bigint not null
);
"""

private const val insertSQL = """
insert into rolelist (
    guildid,
    roleid
)
values ($1, $2);
"""

object RolesTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
            "create unique index if not exists rolelist_index_1 on rolelist (guildid, roleid);"
    )

    private fun PgRowSet.toRoleEntities(): List<RoleEntity> = this.map {
        RoleEntity(
                id = it.getInteger(0),
                guildId = it.getLong(1),
                roleId = it.getLong(2)
        )
    }

    suspend fun fetchRole(guild: Guild, role: Role): RoleEntity? {
        return pgPool.preparedQueryAwait("select * from rolelist where guildid = $1 and roleid = $2;", Tuple.of(guild.idLong, role.idLong))
                .toRoleEntities()
                .firstOrNull()
    }

    suspend fun fetchGuildRoles(guild: Guild): List<RoleEntity> {
        return pgPool.preparedQueryAwait("select * from rolelist where guildid = $1;", Tuple.of(guild.idLong))
                .toRoleEntities()
    }

    suspend fun isSelfAssignable(guild: Guild, role: Role): Boolean = fetchRole(guild, role) != null

    suspend fun insertRole(role: RoleEntity) {
        pgPool.preparedQueryAwait(insertSQL, role.toTuple())
    }

    suspend fun deleteRole(role: RoleEntity) {
        pgPool.preparedQueryAwait("delete from rolelist where id = $1;", Tuple.of(role.id))
    }
}

data class RoleEntity(
    val id: Int = -1,
    val guildId: Long,
    val roleId: Long
) {
    fun toTuple(): Tuple {
        return Tuple.of(
                guildId,
                roleId
        )
    }
}
