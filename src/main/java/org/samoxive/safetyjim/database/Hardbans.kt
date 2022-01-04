package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild

private const val createSQL =
    """
create table if not exists hardbans (
    id                serial not null primary key,
    user_id           bigint not null,
    moderator_user_id bigint not null,
    guild_id          bigint not null,
    hardban_time      bigint not null,
    reason            text not null
);
"""

private const val insertSQL =
    """
insert into hardbans (
    user_id,
    moderator_user_id,
    guild_id,
    hardban_time,
    reason
)
values ($1, $2, $3, $4, $5)
returning *;
"""

private const val updateSQL =
    """
update hardbans set
    user_id = $2,
    moderator_user_id = $3,
    guild_id = $4,
    hardban_time = $5,
    reason = $6
where id = $1;
"""

object HardbansTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf(
        "create index if not exists hardbans_hardban_time_index on hardbans (hardban_time desc);"
    )

    private fun RowSet<Row>.toHardbanEntities(): List<HardbanEntity> = this.map {
        HardbanEntity(
            id = it.getInteger(0),
            userId = it.getLong(1),
            moderatorUserId = it.getLong(2),
            guildId = it.getLong(3),
            hardbanTime = it.getLong(4),
            reason = it.getString(5)
        )
    }

    suspend fun fetchHardban(id: Int): HardbanEntity? {
        return pgPool.preparedQueryAwait("select * from hardbans where id = $1;", Tuple.of(id))
            .toHardbanEntities()
            .firstOrNull()
    }

    suspend fun fetchGuildHardbans(guild: Guild, page: Int): List<HardbanEntity> {
        return pgPool.preparedQueryAwait("select * from hardbans where guild_id = $1 order by hardban_time desc limit 10 offset $2;", Tuple.of(guild.idLong, (page - 1) * 10))
            .toHardbanEntities()
    }

    suspend fun fetchGuildHardbansCount(guild: Guild): Int {
        return pgPool.preparedQueryAwait("select count(*) from hardbans where guild_id = $1;", Tuple.of(guild.idLong))
            .first()
            .getInteger(0)
    }

    suspend fun insertHardban(hardban: HardbanEntity): HardbanEntity {
        return pgPool.preparedQueryAwait(insertSQL, hardban.toTuple())
            .toHardbanEntities()
            .first()
    }

    suspend fun updateHardban(newHardban: HardbanEntity) {
        pgPool.preparedQueryAwait(updateSQL, newHardban.toTupleWithId())
    }
}

data class HardbanEntity(
    val id: Int = -1,
    val userId: Long,
    val moderatorUserId: Long,
    val guildId: Long,
    val hardbanTime: Long,
    val reason: String
) {
    fun toTuple(): Tuple {
        return Tuple.of(
            userId,
            moderatorUserId,
            guildId,
            hardbanTime,
            reason
        )
    }

    fun toTupleWithId(): Tuple {
        return Tuple.of(
            id,
            userId,
            moderatorUserId,
            guildId,
            hardbanTime,
            reason
        )
    }
}
