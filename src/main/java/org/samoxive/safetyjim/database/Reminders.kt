package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple

private const val createSQL = """
create table if not exists reminderlist (
    id serial not null primary key,
    userid bigint not null,
    channelid bigint not null,
    guildid bigint not null,
    createtime bigint not null,
    remindtime bigint not null,
    reminded boolean not null,
    message text not null
);
"""

private const val insertSQL = """
insert into reminderlist (
    userid,
    channelid,
    guildid,
    createtime,
    remindtime,
    reminded,
    message
)
values ($1, $2, $3, $4, $5, $6, $7)
returning *;
"""

private const val updateSQL = """
update reminderlist set
    userid = $2,
    channelid = $3,
    guildid = $4,
    createtime = $5,
    remindtime = $6,
    reminded = $7,
    message = $8
where id = $1;
"""

object RemindersTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun PgRowSet.toReminderEntities(): List<ReminderEntity> = this.map {
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
        return pgPool.preparedQueryAwait("select * from reminderlist where reminded = false and remindtime < $1;", Tuple.of(time))
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