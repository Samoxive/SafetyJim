package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple
import net.dv8tion.jda.core.entities.Guild

private const val createSQL = """
create table if not exists softbanlist (
    id serial not null primary key,
    userid bigint not null,
    moderatoruserid bigint not null,
    guildid bigint not null,
    softbantime bigint not null,
    reason text not null
);
"""

private const val insertSQL = """
insert into softbanlist (
    userid,
    moderatoruserid,
    guildid,
    softbantime,
    reason
)
values ($1, $2, $3, $4, $5)
returning *;
"""

private const val updateSQL = """
update softbanlist set
    userid = $2,
    moderatoruserid = $3,
    guildid = $4,
    softbantime = $5,
    reason = $6
where id = $1;
"""

object SoftbansTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun PgRowSet.toSoftbanEntities(): List<SoftbanEntity> = this.map {
        SoftbanEntity(
                id = it.getInteger(0),
                userId = it.getLong(1),
                moderatorUserId = it.getLong(2),
                guildId = it.getLong(3),
                softbanTime = it.getLong(4),
                reason = it.getString(5)
        )
    }

    suspend fun fetchGuildSoftbans(guild: Guild): List<SoftbanEntity> {
        return pgPool.preparedQueryAwait("select * from softbanlist where guildid = $1;", Tuple.of(guild.idLong))
                .toSoftbanEntities()
    }

    suspend fun insertSoftban(softban: SoftbanEntity): SoftbanEntity {
        return pgPool.preparedQueryAwait(insertSQL, softban.toTuple())
                .toSoftbanEntities()
                .first()
    }

    suspend fun updateSoftban(newSoftban: SoftbanEntity) {
        pgPool.preparedQueryAwait(updateSQL, newSoftban.toTupleWithId())
    }
}

data class SoftbanEntity(
    val id: Int = -1,
    val userId: Long,
    val moderatorUserId: Long,
    val guildId: Long,
    val softbanTime: Long,
    val reason: String
) {
    fun toTuple(): Tuple {
        return Tuple.of(
                userId,
                moderatorUserId,
                guildId,
                softbanTime,
                reason
        )
    }

    fun toTupleWithId(): Tuple {
        return Tuple.of(
                id,
                userId,
                moderatorUserId,
                guildId,
                softbanTime,
                reason
        )
    }
}