package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

private const val createSQL =
    """
create table if not exists mutes (
    id                serial not null primary key,
    user_id           bigint not null,
    moderator_user_id bigint not null,
    guild_id          bigint not null,
    mute_time         bigint not null,
    expire_time       bigint not null,
    reason            text not null,
    expires           boolean not null,
    unmuted           boolean not null,
    pardoned          boolean not null
);
"""

private const val insertSQL =
    """
insert into mutes (
    user_id,
    moderator_user_id,
    guild_id,
    mute_time,
    expire_time,
    reason,
    expires,
    unmuted,
    pardoned
)
values ($1, $2, $3, $4, $5, $6, $7, $8, $9)
returning *;
"""

private const val updateSQL =
    """
update mutes set
    user_id = $2,
    moderator_user_id = $3,
    guild_id = $4,
    mute_time = $5,
    expire_time = $6,
    reason = $7,
    expires = $8,
    unmuted = $9,
    pardoned = $10
where id = $1;
"""

object MutesTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun RowSet<Row>.toMuteEntities(): List<MuteEntity> = this.map {
        MuteEntity(
            id = it.getInteger(0),
            userId = it.getLong(1),
            moderatorUserId = it.getLong(2),
            guildId = it.getLong(3),
            muteTime = it.getLong(4),
            expireTime = it.getLong(5),
            reason = it.getString(6),
            expires = it.getBoolean(7),
            unmuted = it.getBoolean(8),
            pardoned = it.getBoolean(9)
        )
    }

    suspend fun fetchMute(id: Int): MuteEntity? {
        return pgPool.preparedQueryAwait("select * from mutes where id = $1;", Tuple.of(id))
            .toMuteEntities()
            .firstOrNull()
    }

    suspend fun fetchGuildMutes(guild: Guild, page: Int): List<MuteEntity> {
        return pgPool.preparedQueryAwait("select * from mutes where guild_id = $1 order by mute_time desc limit 10 offset $2;", Tuple.of(guild.idLong, (page - 1) * 10))
            .toMuteEntities()
    }

    suspend fun fetchGuildMutesCount(guild: Guild): Int {
        return pgPool.preparedQueryAwait("select count(*) from mutes where guild_id = $1;", Tuple.of(guild.idLong))
            .first()
            .getInteger(0)
    }

    suspend fun fetchExpiredMutes(): List<MuteEntity> {
        val time = System.currentTimeMillis() / 1000
        return pgPool.preparedQueryAwait("select * from mutes where unmuted = false and expires = true and expire_time < $1;", Tuple.of(time))
            .toMuteEntities()
    }

    suspend fun fetchValidUserMutes(guild: Guild, user: User): List<MuteEntity> {
        return pgPool.preparedQueryAwait("select * from mutes where guild_id = $1 and user_id = $2 and unmuted = false;", Tuple.of(guild.idLong, user.idLong))
            .toMuteEntities()
    }

    suspend fun fetchUserActionableMuteCount(guild: Guild, user: User): Int {
        return pgPool.preparedQueryAwait("select count(*) from mutes where guild_id = $1 and user_id = $2 and pardoned = false;", Tuple.of(guild.idLong, user.idLong))
            .first()
            .getInteger(0)
    }

    suspend fun insertMute(mute: MuteEntity): MuteEntity {
        return pgPool.preparedQueryAwait(insertSQL, mute.toTuple())
            .toMuteEntities()
            .first()
    }

    suspend fun updateMute(newMute: MuteEntity) {
        pgPool.preparedQueryAwait(updateSQL, newMute.toTupleWithId())
    }

    suspend fun invalidatePreviousUserMutes(guild: Guild, user: User) {
        pgPool.preparedQueryAwait("update mutes set unmuted = true where guild_id = $1 and user_id = $2;", Tuple.of(guild.idLong, user.idLong))
    }
}

data class MuteEntity(
    val id: Int = -1,
    val userId: Long,
    val moderatorUserId: Long,
    val guildId: Long,
    val muteTime: Long,
    val expireTime: Long,
    val reason: String,
    val expires: Boolean,
    val unmuted: Boolean,
    val pardoned: Boolean
) {
    fun toTuple(): Tuple {
        return Tuple.of(
            userId,
            moderatorUserId,
            guildId,
            muteTime,
            expireTime,
            reason,
            expires,
            unmuted,
            pardoned
        )
    }

    fun toTupleWithId(): Tuple {
        return Tuple.of(
            id,
            userId,
            moderatorUserId,
            guildId,
            muteTime,
            expireTime,
            reason,
            expires,
            unmuted,
            pardoned
        )
    }
}
