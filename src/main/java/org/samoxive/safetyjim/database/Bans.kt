package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

private const val createSQL =
    """
create table if not exists bans (
    id                serial  not null primary key,
    user_id           bigint  not null,
    moderator_user_id bigint  not null,
    guild_id          bigint  not null,
    ban_time          bigint  not null,
    expire_time       bigint  not null,
    reason            text    not null,
    expires           boolean not null,
    unbanned          boolean not null
);
"""

private const val insertSQL =
    """
insert into bans (
    user_id,
    moderator_user_id,
    guild_id,
    ban_time,
    expire_time,
    reason,
    expires,
    unbanned
)
values ($1, $2, $3, $4, $5, $6, $7, $8)
returning *;
"""

private const val updateSQL =
    """
update bans set
    user_id = $2,
    moderator_user_id = $3,
    guild_id = $4,
    ban_time = $5,
    expire_time = $6,
    reason = $7,
    expires = $8,
    unbanned = $9
where id = $1;
"""

object BansTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
        "create index if not exists bans_ban_time_index on bans (ban_time desc);"
    )

    private fun RowSet<Row>.toBanEntities(): List<BanEntity> = this.map {
        BanEntity(
            id = it.getInteger(0),
            userId = it.getLong(1),
            moderatorUserId = it.getLong(2),
            guildId = it.getLong(3),
            banTime = it.getLong(4),
            expireTime = it.getLong(5),
            reason = it.getString(6),
            expires = it.getBoolean(7),
            unbanned = it.getBoolean(8)
        )
    }

    suspend fun fetchBan(id: Int): BanEntity? {
        return pgPool.preparedQueryAwait("select * from bans where id = $1;", Tuple.of(id))
            .toBanEntities()
            .firstOrNull()
    }

    suspend fun fetchGuildBans(guild: Guild, page: Int): List<BanEntity> {
        return pgPool.preparedQueryAwait("select * from bans where guild_id = $1 order by ban_time desc limit 10 offset $2;", Tuple.of(guild.idLong, (page - 1) * 10))
            .toBanEntities()
    }

    suspend fun fetchGuildBansCount(guild: Guild): Int {
        return pgPool.preparedQueryAwait("select count(*) from bans where guild_id = $1;", Tuple.of(guild.idLong))
            .first()
            .getInteger(0)
    }

    suspend fun fetchExpiredBans(): List<BanEntity> {
        val time = System.currentTimeMillis() / 1000
        return pgPool.preparedQueryAwait("select * from bans where unbanned = false and expires = true and expire_time < $1;", Tuple.of(time))
            .toBanEntities()
    }

    suspend fun fetchGuildLastBan(guild: Guild): BanEntity? {
        return pgPool.preparedQueryAwait("select * from bans where guild_id = $1 order by ban_time desc limit 1;", Tuple.of(guild.idLong))
            .toBanEntities()
            .firstOrNull()
    }

    suspend fun insertBan(ban: BanEntity): BanEntity {
        return pgPool.preparedQueryAwait(insertSQL, ban.toTuple())
            .toBanEntities()
            .first()
    }

    suspend fun updateBan(newBan: BanEntity) {
        pgPool.preparedQueryAwait(updateSQL, newBan.toTupleWithId())
    }

    suspend fun invalidatePreviousUserBans(guild: Guild, user: User) {
        pgPool.preparedQueryAwait("update bans set unbanned = true where guild_id = $1 and user_id = $2;", Tuple.of(guild.idLong, user.idLong))
    }
}

data class BanEntity(
    val id: Int = -1,
    val userId: Long,
    val moderatorUserId: Long,
    val guildId: Long,
    val banTime: Long,
    val expireTime: Long,
    val reason: String,
    val expires: Boolean,
    val unbanned: Boolean
) {
    fun toTuple(): Tuple {
        return Tuple.of(
            userId,
            moderatorUserId,
            guildId,
            banTime,
            expireTime,
            reason,
            expires,
            unbanned
        )
    }

    fun toTupleWithId(): Tuple {
        return Tuple.of(
            id,
            userId,
            moderatorUserId,
            guildId,
            banTime,
            expireTime,
            reason,
            expires,
            unbanned
        )
    }
}
