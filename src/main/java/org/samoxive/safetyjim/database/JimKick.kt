package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimKickTable : IntIdTable(name = "kicklist") {
    val userid = text("userid")
    val moderatoruserid = text("moderatoruserid")
    val guildid = text("guildid")
    val kicktime = long("kicktime")
    val reason = text("reason")
}

class JimKick(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimKick>(JimKickTable)

    var userid by JimKickTable.userid
    var moderatoruserid by JimKickTable.moderatoruserid
    var guildid by JimKickTable.guildid
    var kicktime by JimKickTable.kicktime
    var reason by JimKickTable.reason
}