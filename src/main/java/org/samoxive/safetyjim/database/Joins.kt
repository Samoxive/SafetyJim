package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

private const val createSQL =
    """
create table if not exists joins (
    id         serial not null primary key,
    user_id    bigint not null,
    guild_id   bigint not null,
    join_time  bigint not null,
    allow_time bigint not null,
    allowed    boolean not null
);
"""

private const val insertSQL =
    """
insert into joins (
    user_id,
    guild_id,
    join_time,
    allow_time,
    allowed
)
values ($1, $2, $3, $4, $5)
returning *;
"""

private const val updateSQL =
    """
update joins set
    user_id = $2,
    guild_id = $3,
    join_time = $4,
    allow_time = $5,
    allowed = $6
where id = $1;
"""

object JoinsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun RowSet<Row>.toJoinEntities(): List<JoinEntity> = this.map {
        JoinEntity(
            id = it.getInteger(0),
            userId = it.getLong(1),
            guildId = it.getLong(2),
            joinTime = it.getLong(3),
            allowTime = it.getLong(4),
            allowed = it.getBoolean(5)
        )
    }

    suspend fun fetchExpiredJoins(): List<JoinEntity> {
        val time = System.currentTimeMillis() / 1000
        return pgPool.preparedQueryAwait("select * from joins where allowed = false and allow_time < $1;", Tuple.of(time))
            .toJoinEntities()
    }

    suspend fun insertJoin(join: JoinEntity): JoinEntity {
        return pgPool.preparedQueryAwait(insertSQL, join.toTuple())
            .toJoinEntities()
            .first()
    }

    suspend fun updateJoin(newJoin: JoinEntity) {
        pgPool.preparedQueryAwait(updateSQL, newJoin.toTupleWithId())
    }

    suspend fun deleteUserJoins(guild: Guild, user: User) {
        pgPool.preparedQueryAwait("delete from joins where guild_id = $1 and user_id = $2;", Tuple.of(guild.idLong, user.idLong))
    }
}

data class JoinEntity(
    val id: Int = -1,
    val userId: Long,
    val guildId: Long,
    val joinTime: Long,
    val allowTime: Long,
    val allowed: Boolean
) {
    fun toTuple(): Tuple {
        return Tuple.of(
            userId,
            guildId,
            joinTime,
            allowTime,
            allowed
        )
    }

    fun toTupleWithId(): Tuple {
        return Tuple.of(
            id,
            userId,
            guildId,
            joinTime,
            allowTime,
            allowed
        )
    }
}
