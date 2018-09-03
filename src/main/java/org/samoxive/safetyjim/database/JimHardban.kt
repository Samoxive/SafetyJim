package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimHardbanTable : IntIdTable(name = "hardbanlist") {
    val userid = text("userid")
    val moderatoruserid = text("moderatoruserid")
    val guildid = text("guildid")
    val hardbantime = long("hardbantime")
    val reason = text("reason")
}

class JimHardban(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimHardban>(JimHardbanTable)

    var userid by JimHardbanTable.userid
    var moderatoruserid by JimHardbanTable.moderatoruserid
    var guildid by JimHardbanTable.guildid
    var hardbantime by JimHardbanTable.hardbantime
    var reason by JimHardbanTable.reason
}