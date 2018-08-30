package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimMemberCountTable : IntIdTable(name = "membercounts") {
    val guildid = text("guildid")
    val date = long("date")
    val onlinecount = integer("onlinecount")
    val count = integer("count")
}

class JimMemberCount(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimMemberCount>(JimMemberCountTable)

    var guildid by JimMemberCountTable.guildid
    var date by JimMemberCountTable.date
    var onlinecount by JimMemberCountTable.onlinecount
    var count by JimMemberCountTable.count
}