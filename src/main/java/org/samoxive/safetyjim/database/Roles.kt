package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role

private const val createSQL =
    """
create table if not exists iam_roles (
    id       serial not null primary key,
    guild_id bigint not null,
    role_id  bigint not null
);
"""

private const val insertSQL =
    """
insert into iam_roles (
    guild_id,
    role_id
)
values ($1, $2);
"""

object RolesTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
        "create unique index if not exists iam_roles_guild_id_role_id_index on iam_roles (guild_id, role_id);"
    )

    private fun RowSet<Row>.toRoleEntities(): List<RoleEntity> = this.map {
        RoleEntity(
            id = it.getInteger(0),
            guildId = it.getLong(1),
            roleId = it.getLong(2)
        )
    }

    suspend fun fetchRole(guild: Guild, role: Role): RoleEntity? {
        return pgPool.preparedQueryAwait("select * from iam_roles where guild_id = $1 and role_id = $2;", Tuple.of(guild.idLong, role.idLong))
            .toRoleEntities()
            .firstOrNull()
    }

    suspend fun fetchGuildRoles(guild: Guild): List<RoleEntity> {
        return pgPool.preparedQueryAwait("select * from iam_roles where guild_id = $1;", Tuple.of(guild.idLong))
            .toRoleEntities()
    }

    suspend fun isSelfAssignable(guild: Guild, role: Role): Boolean = fetchRole(guild, role) != null

    suspend fun insertRole(role: RoleEntity) {
        pgPool.preparedQueryAwait(insertSQL, role.toTuple())
    }

    suspend fun deleteRole(role: RoleEntity) {
        pgPool.preparedQueryAwait("delete from iam_roles where id = $1;", Tuple.of(role.id))
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
