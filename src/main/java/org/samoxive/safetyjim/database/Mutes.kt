package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

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
    unmuted boolean not null,
    pardoned boolean not null
);
"""

private const val insertSQL = """
insert into mutelist (
    userid,
    moderatoruserid,
    guildid,
    mutetime,
    expiretime,
    reason,
    expires,
    unmuted,
    pardoned
)
values ($1, $2, $3, $4, $5, $6, $7, $8, $9)
returning *;
"""

private const val updateSQL = """
update mutelist set
    userid = $2,
    moderatoruserid = $3,
    guildid = $4,
    mutetime = $5,
    expiretime = $6,
    reason = $7,
    expires = $8,
    unmuted = $9,
    pardoned = $10
where id = $1;
"""

object MutesTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun PgRowSet.toMuteEntities(): List<MuteEntity> = this.map {
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

    suspend fun fetchGuildMutes(guild: Guild, page: Int): List<MuteEntity> {
        return pgPool.preparedQueryAwait("select * from mutelist where guildid = $1 order by mutetime desc limit 10 offset $2;", Tuple.of(guild.idLong, (page - 1) * 10))
                .toMuteEntities()
    }

    suspend fun fetchGuildMutesCount(guild: Guild): Int {
        return pgPool.preparedQueryAwait("select count(*) from mutelist where guildid = $1;", Tuple.of(guild.idLong))
                .first()
                .getInteger(0)
    }

    suspend fun fetchExpiredMutes(): List<MuteEntity> {
        val time = System.currentTimeMillis() / 1000
        return pgPool.preparedQueryAwait("select * from mutelist where unmuted = false and expires = true and expiretime < $1;", Tuple.of(time))
                .toMuteEntities()
    }

    suspend fun fetchValidUserMutes(guild: Guild, user: User): List<MuteEntity> {
        return pgPool.preparedQueryAwait("select * from mutelist where guildid = $1 and userid = $2 and unmuted = false;", Tuple.of(guild.idLong, user.idLong))
                .toMuteEntities()
    }

    suspend fun fetchUserActionableMuteCount(guild: Guild, user: User): Int {
        return pgPool.preparedQueryAwait("select count(*) from mutelist where guildid = $1 and userid = $2;", Tuple.of(guild.idLong, user.idLong))
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
        pgPool.preparedQueryAwait("update mutelist set unmuted = true where guildid = $1 and userid = $2;", Tuple.of(guild.idLong, user.idLong))
    }
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
