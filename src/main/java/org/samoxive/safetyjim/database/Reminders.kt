package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple

private const val createSQL =
    """
create table if not exists reminders (
    id          serial not null primary key,
    user_id     bigint not null,
    channel_id  bigint not null,
    guild_id    bigint not null,
    create_time bigint not null,
    remind_time bigint not null,
    reminded    boolean not null,
    message     text not null
);
"""

private const val insertSQL =
    """
insert into reminders (
    user_id,
    channel_id,
    guild_id,
    create_time,
    remind_time,
    reminded,
    message
)
values ($1, $2, $3, $4, $5, $6, $7)
returning *;
"""

private const val updateSQL =
    """
update reminders set
    user_id = $2,
    channel_id = $3,
    guild_id = $4,
    create_time = $5,
    remind_time = $6,
    reminded = $7,
    message = $8
where id = $1;
"""

object RemindersTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun RowSet<Row>.toReminderEntities(): List<ReminderEntity> = this.map {
        ReminderEntity(
            id = it.getInteger(0),
            userId = it.getLong(1),
            channelId = it.getLong(2),
            guildId = it.getLong(3),
            createTime = it.getLong(4),
            remindTime = it.getLong(5),
            reminded = it.getBoolean(6),
            message = it.getString(7)
        )
    }

    suspend fun fetchExpiredReminders(): List<ReminderEntity> {
        val time = System.currentTimeMillis() / 1000
        return pgPool.preparedQueryAwait("select * from reminders where reminded = false and remind_time < $1;", Tuple.of(time))
            .toReminderEntities()
    }

    suspend fun insertReminder(reminder: ReminderEntity): ReminderEntity {
        return pgPool.preparedQueryAwait(insertSQL, reminder.toTuple())
            .toReminderEntities()
            .first()
    }

    suspend fun updateReminder(newReminder: ReminderEntity) {
        pgPool.preparedQueryAwait(updateSQL, newReminder.toTupleWithId())
    }
}

data class ReminderEntity(
    val id: Int = -1,
    val userId: Long,
    val channelId: Long,
    val guildId: Long,
    val createTime: Long,
    val remindTime: Long,
    val reminded: Boolean,
    val message: String
) {
    fun toTuple(): Tuple {
        return Tuple.of(
            userId,
            channelId,
            guildId,
            createTime,
            remindTime,
            reminded,
            message
        )
    }

    fun toTupleWithId(): Tuple {
        return Tuple.of(
            id,
            userId,
            channelId,
            guildId,
            createTime,
            remindTime,
            reminded,
            message
        )
    }
}
