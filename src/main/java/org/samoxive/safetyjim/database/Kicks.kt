package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

private const val createSQL =
    """
create table if not exists kicks (
    id                serial not null primary key,
    user_id           bigint not null,
    moderator_user_id bigint not null,
    guild_id          bigint not null,
    kick_time         bigint not null,
    reason            text not null,
    pardoned          boolean not null
);
"""

private const val insertSQL =
    """
insert into kicks (
    user_id,
    moderator_user_id,
    guild_id,
    kick_time,
    reason,
    pardoned
)
values ($1, $2, $3, $4, $5, $6)
returning *;
"""

private const val updateSQL =
    """
update kicks set
    user_id = $2,
    moderator_user_id = $3,
    guild_id = $4,
    kick_time = $5,
    reason = $6,
    pardoned = $7
where id = $1;
"""

object KicksTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun RowSet<Row>.toKickEntities(): List<KickEntity> = this.map {
        KickEntity(
            id = it.getInteger(0),
            userId = it.getLong(1),
            moderatorUserId = it.getLong(2),
            guildId = it.getLong(3),
            kickTime = it.getLong(4),
            reason = it.getString(5),
            pardoned = it.getBoolean(6)
        )
    }

    suspend fun fetchKick(id: Int): KickEntity? {
        return pgPool.preparedQueryAwait("select * from kicks where id = $1;", Tuple.of(id))
            .toKickEntities()
            .firstOrNull()
    }

    suspend fun fetchGuildKicks(guild: Guild, page: Int): List<KickEntity> {
        return pgPool.preparedQueryAwait("select * from kicks where guild_id = $1 order by kick_time desc limit 10 offset $2;", Tuple.of(guild.idLong, (page - 1) * 10))
            .toKickEntities()
    }

    suspend fun fetchGuildKicksCount(guild: Guild): Int {
        return pgPool.preparedQueryAwait("select count(*) from kicks where guild_id = $1;", Tuple.of(guild.idLong))
            .first()
            .getInteger(0)
    }

    suspend fun fetchUserActionableKickCount(guild: Guild, user: User): Int {
        return pgPool.preparedQueryAwait("select count(*) from kicks where guild_id = $1 and user_id = $2 and pardoned = false;", Tuple.of(guild.idLong, user.idLong))
            .first()
            .getInteger(0)
    }

    suspend fun insertKick(kick: KickEntity): KickEntity {
        return pgPool.preparedQueryAwait(insertSQL, kick.toTuple())
            .toKickEntities()
            .first()
    }

    suspend fun updateKick(newKick: KickEntity) {
        pgPool.preparedQueryAwait(updateSQL, newKick.toTupleWithId())
    }
}

data class KickEntity(
    val id: Int = -1,
    val userId: Long,
    val moderatorUserId: Long,
    val guildId: Long,
    val kickTime: Long,
    val reason: String,
    val pardoned: Boolean
) {
    fun toTuple(): Tuple {
        return Tuple.of(
            userId,
            moderatorUserId,
            guildId,
            kickTime,
            reason,
            pardoned
        )
    }

    fun toTupleWithId(): Tuple {
        return Tuple.of(
            id,
            userId,
            moderatorUserId,
            guildId,
            kickTime,
            reason,
            pardoned
        )
    }
}
