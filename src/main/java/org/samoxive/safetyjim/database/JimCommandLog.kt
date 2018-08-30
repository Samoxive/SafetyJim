package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimCommandLogTable : IntIdTable(name = "commandlogs") {
    val command = text("command")
    val arguments = text("arguments")
    val time = datetime("time")
    val username = text("username")
    val userid = text("userid")
    val guildname = text("guildname")
    val guildid = text("guildid")
    val executiontime = integer("executiontime")
}

class JimCommandLog(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimCommandLog>(JimCommandLogTable)

    var command by JimCommandLogTable.command
    var arguments by JimCommandLogTable.arguments
    var time by JimCommandLogTable.time
    var username by JimCommandLogTable.username
    var userid by JimCommandLogTable.userid
    var guildname by JimCommandLogTable.guildname
    var guildid by JimCommandLogTable.guildid
    var executiontime by JimCommandLogTable.executiontime
}