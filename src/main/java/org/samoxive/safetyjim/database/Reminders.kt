package org.samoxive.safetyjim.database

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

object RemindersTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
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
)