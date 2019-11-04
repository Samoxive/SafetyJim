package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

private const val createSQL = """
create table if not exists warnlist (
    id serial not null primary key,
    userid bigint not null,
    moderatoruserid bigint not null,
    guildid bigint not null,
    warntime bigint not null,
    reason text not null,
    pardoned boolean not null
);
"""

private const val insertSQL = """
insert into warnlist (
    userid,
    moderatoruserid,
    guildid,
    warntime,
    reason,
    pardoned
)
values ($1, $2, $3, $4, $5, $6)
returning *;
"""

private const val updateSQL = """
update warnlist set
    userid = $2,
    moderatoruserid = $3,
    guildid = $4,
    warntime = $5,
    reason = $6,
    pardoned = $7
where id = $1;
"""

object WarnsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun PgRowSet.toWarnEntities(): List<WarnEntity> = this.map {
        WarnEntity(
                id = it.getInteger(0),
                userId = it.getLong(1),
                moderatorUserId = it.getLong(2),
                guildId = it.getLong(3),
                warnTime = it.getLong(4),
                reason = it.getString(5),
                pardoned = it.getBoolean(6)
        )
    }

    suspend fun fetchGuildWarns(guild: Guild, page: Int): List<WarnEntity> {
        return pgPool.preparedQueryAwait("select * from warnlist where guildid = $1 order by warntime desc limit 10 offset $2;", Tuple.of(guild.idLong, (page - 1) * 10))
                .toWarnEntities()
    }

    suspend fun fetchGuildWarnsCount(guild: Guild): Int {
        return pgPool.preparedQueryAwait("select count(*) from warnlist where guildid = $1;", Tuple.of(guild.idLong))
                .first()
                .getInteger(0)
    }

    suspend fun fetchUserActionableSoftbanCount(guild: Guild, user: User): Int {
        return pgPool.preparedQueryAwait("select count(*) from warnlist where guildid = $1 and userid = $2;", Tuple.of(guild.idLong, user.idLong))
                .first()
                .getInteger(0)
    }

    suspend fun insertWarn(warn: WarnEntity): WarnEntity {
        return pgPool.preparedQueryAwait(insertSQL, warn.toTuple())
                .toWarnEntities()
                .first()
    }

    suspend fun updateWarn(newWarn: WarnEntity) {
        pgPool.preparedQueryAwait(updateSQL, newWarn.toTupleWithId())
    }
}

data class WarnEntity(
    val id: Int = -1,
    val userId: Long,
    val moderatorUserId: Long,
    val guildId: Long,
    val warnTime: Long,
    val reason: String,
    val pardoned: Boolean
) {
    fun toTuple(): Tuple {
        return Tuple.of(
                userId,
                moderatorUserId,
                guildId,
                warnTime,
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
                warnTime,
                reason,
                pardoned
        )
    }
}
