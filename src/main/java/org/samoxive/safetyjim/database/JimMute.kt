package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimMuteTable : IntIdTable(name = "mutelist") {
    val userid = text("userid")
    val moderatoruserid = text("moderatoruserid")
    val guildid = text("guildid")
    val mutetime = long("mutetime")
    val expiretime = long("expiretime").nullable()
    val reason = text("reason")
    val expires = bool("expires")
    val unmuted = bool("unmuted")
}

class JimMute(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimMute>(JimMuteTable)

    var userid by JimMuteTable.userid
    var moderatoruserid by JimMuteTable.moderatoruserid
    var guildid by JimMuteTable.guildid
    var mutetime by JimMuteTable.mutetime
    var expiretime by JimMuteTable.expiretime
    var reason by JimMuteTable.reason
    var expires by JimMuteTable.expires
    var unmuted by JimMuteTable.unmuted
}