package org.samoxive.safetyjim.database

private const val createSQL = """
create table if not exists messages (
    messageid bigint not null primary key,
    userid bigint not null,
    guildid bigint not null,
    channelid bigint not null,
    date bigint not null,
    wordcount integer not null,
    size integer not null
);
"""

object MessagesTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()
}

data class MessageEntity(
        val messageId: Long,
        val userId: Long,
        val guildId: Long,
        val channelId: Long,
        val date: Long,
        val wordCount: Int,
        val size: Int
)