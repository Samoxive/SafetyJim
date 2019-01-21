package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimJoinTable : IntIdTable(name = "joinlist") {
    val userid = long("userid")
    val guildid = long("guildid")
    val jointime = long("jointime")
    val allowtime = long("allowtime")
    val allowed = bool("allowed")
}

class JimJoin(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimJoin>(JimJoinTable)

    var userid by JimJoinTable.userid
    var guildid by JimJoinTable.guildid
    var jointime by JimJoinTable.jointime
    var allowtime by JimJoinTable.allowtime
    var allowed by JimJoinTable.allowed
}