package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple
import net.dv8tion.jda.core.entities.Guild

private const val createSQL = """
create table if not exists banlist (
    id              serial  not null primary key,
    userid          bigint  not null,
    moderatoruserid bigint  not null,
    guildid         bigint  not null,
    bantime         bigint  not null,
    expiretime      bigint,
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
VALUES ($2, $3, $4, $5, $6, $7, $8, $9)
returning *;
"""

object BansTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

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

    suspend fun fetchGuildBans(guild: Guild): List<BanEntity> {
        return pgPool.preparedQueryAwait("select * from banlist where guildid = $1;", Tuple.of(guild.idLong))
                .toBanEntities()
    }

    suspend fun insertBan(ban: BanEntity): BanEntity {
        return pgPool.preparedQueryAwait(insertSQL, ban.toTuple())
                .toBanEntities()
                .first()
    }
}

data class BanEntity(
        val id: Int = -1,
        val userId: Long,
        val moderatorUserId: Long,
        val guildId: Long,
        val banTime: Long,
        val expireTime: Long?,
        val reason: String,
        val expires: Boolean,
        val unbanned: Boolean
) {
    fun toTuple(): Tuple {
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