package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimRoleTable : IntIdTable(name = "rolelist") {
    val guildid = long("guildid")
    val roleid = long("roleid")
}

class JimRole(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimRole>(JimRoleTable)

    var guildid by JimRoleTable.guildid
    var roleid by JimRoleTable.roleid
}