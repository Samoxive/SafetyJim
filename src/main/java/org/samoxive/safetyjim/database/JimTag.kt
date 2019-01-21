package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimTagTable : IntIdTable(name = "taglist") {
    val guildid = long("guildid")
    val name = text("name")
    val response = text("response")
}

class JimTag(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimTag>(JimTagTable)

    var guildid by JimTagTable.guildid
    var name by JimTagTable.name
    var response by JimTagTable.response
}