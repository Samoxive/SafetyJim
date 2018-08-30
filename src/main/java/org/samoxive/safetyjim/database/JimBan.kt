package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimBanTable : IntIdTable(name = "banlist") {
    val userid = text("userid")
    val moderatoruserid = text("moderatoruserid")
    val guildid = text("guildid")
    val bantime = long("bantime")
    val expiretime = long("expiretime")
    val reason = text("reason")
    val expires = bool("expires")
    val unbanned = bool("unbanned")
}

class JimBan(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimBan>(JimBanTable)

    var userid by JimBanTable.userid
    var moderatoruserid by JimBanTable.moderatoruserid
    var guildid by JimBanTable.guildid
    var bantime by JimBanTable.bantime
    var expiretime by JimBanTable.expiretime
    var reason by JimBanTable.reason
    var expires by JimBanTable.expires
    var unbanned by JimBanTable.unbanned
}
