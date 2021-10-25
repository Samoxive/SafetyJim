package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

private const val createSQL =
    """
create table if not exists joinlist (
    id serial not null primary key,
    userid bigint not null,
    guildid bigint not null,
    jointime bigint not null,
    allowtime bigint not null,
    allowed boolean not null
);
"""

private const val insertSQL =
    """
insert into joinlist (
    userid,
    guildid,
    jointime,
    allowtime,
    allowed
)
values ($1, $2, $3, $4, $5)
returning *;
"""

private const val updateSQL =
    """
update joinlist set
    userid = $2,
    guildid = $3,
    jointime = $4,
    allowtime = $5,
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
        return pgPool.preparedQueryAwait("select * from joinlist where allowed = false and allowtime < $1;", Tuple.of(time))
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
        pgPool.preparedQueryAwait("delete from joinlist where guildid = $1 and userid = $2;", Tuple.of(guild.idLong, user.idLong))
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
