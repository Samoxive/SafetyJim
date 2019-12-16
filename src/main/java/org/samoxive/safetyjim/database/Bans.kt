package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

private const val createSQL = """
create table if not exists banlist (
    id              serial  not null primary key,
    userid          bigint  not null,
    moderatoruserid bigint  not null,
    guildid         bigint  not null,
    bantime         bigint  not null,
    expiretime      bigint  not null,
    reason          text    not null,
    expires         boolean not null,
    unbanned        boolean not null
);
"""

private const val insertSQL = """
insert into banlist (
    userid,
    moderatoruserid,
    guildid,
    bantime,
    expiretime,
    reason,
    expires,
    unbanned
)
values ($1, $2, $3, $4, $5, $6, $7, $8)
returning *;
"""

private const val updateSQL = """
update banlist set
    userid = $2,
    moderatoruserid = $3,
    guildid = $4,
    bantime = $5,
    expiretime = $6,
    reason = $7,
    expires = $8,
    unbanned = $9
where id = $1;
"""

object BansTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
        "create index if not exists banlist_bantime_index on banlist (bantime desc);"
    )

    private fun PgRowSet.toBanEntities(): List<BanEntity> = this.map {
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
        return pgPool.preparedQueryAwait("select * from banlist where id = $1;", Tuple.of(id))
            .toBanEntities()
            .firstOrNull()
    }

    suspend fun fetchGuildBans(guild: Guild, page: Int): List<BanEntity> {
        return pgPool.preparedQueryAwait("select * from banlist where guildid = $1 order by bantime desc limit 10 offset $2;", Tuple.of(guild.idLong, (page - 1) * 10))
            .toBanEntities()
    }

    suspend fun fetchGuildBansCount(guild: Guild): Int {
        return pgPool.preparedQueryAwait("select count(*) from banlist where guildid = $1;", Tuple.of(guild.idLong))
            .first()
            .getInteger(0)
    }

    suspend fun fetchExpiredBans(): List<BanEntity> {
        val time = System.currentTimeMillis() / 1000
        return pgPool.preparedQueryAwait("select * from banlist where unbanned = false and expires = true and expiretime < $1;", Tuple.of(time))
            .toBanEntities()
    }

    suspend fun fetchGuildLastBan(guild: Guild): BanEntity? {
        return pgPool.preparedQueryAwait("select * from banlist where guildid = $1 order by bantime desc limit 1;", Tuple.of(guild.idLong))
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
        pgPool.preparedQueryAwait("update banlist set unbanned = true where guildid = $1 and userid = $2;", Tuple.of(guild.idLong, user.idLong))
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
