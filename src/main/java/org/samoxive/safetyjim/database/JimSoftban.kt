package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimSoftbanTable : IntIdTable(name = "softbanlist") {
    val userid = text("userid")
    val moderatoruserid = text("moderatoruserid")
    val guildid = text("guildid")
    val softbantime = long("softbantime")
    val deletedays = integer("deletedays")
    val reason = text("reason")
}

class JimSoftban(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimSoftban>(JimSoftbanTable)

    var userid by JimSoftbanTable.userid
    var moderatoruserid by JimSoftbanTable.moderatoruserid
    var guildid by JimSoftbanTable.guildid
    var softbantime by JimSoftbanTable.softbantime
    var deletedays by JimSoftbanTable.deletedays
    var reason by JimSoftbanTable.reason
}