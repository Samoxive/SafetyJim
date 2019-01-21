package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable

object JimMessageTable : IdTable<String>(name = "messages") {
    override val id = text("messageid").primaryKey().entityId()
    val userid = long("userid")
    val guildid = long("guildid")
    val channelid = long("channelid")
    val date = long("date")
    val wordcount = integer("wordcount")
    val size = integer("size")
}

class JimMessage(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, JimMessage>(JimMessageTable)

    var userid by JimMessageTable.userid
    var guildid by JimMessageTable.guildid
    var channelid by JimMessageTable.channelid
    var date by JimMessageTable.date
    var wordcount by JimMessageTable.wordcount
    var size by JimMessageTable.size
}
