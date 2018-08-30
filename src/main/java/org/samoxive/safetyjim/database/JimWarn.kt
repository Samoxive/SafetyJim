package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimWarnTable : IntIdTable(name = "warnlist") {
    val userid = text("userid")
    val moderatoruserid = text("moderatoruserid")
    val guildid = text("guildid")
    val warntime = long("warntime")
    val reason = text("reason")
}

class JimWarn(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimWarn>(JimWarnTable)

    var userid by JimWarnTable.userid
    var moderatoruserid by JimWarnTable.moderatoruserid
    var guildid by JimWarnTable.guildid
    var warntime by JimWarnTable.warntime
    var reason by JimWarnTable.reason
}