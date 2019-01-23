package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable

object JimSettingsTable : IdTable<Long>(name = "settings") {
    override val id = long("guildid").primaryKey().entityId()
    val modlog = bool("modlog")
    val modlogchannelid = long("modlogchannelid")
    val holdingroom = bool("holdingroom")
    val holdingroomroleid = long("holdingroomroleid").nullable()
    val holdingroomminutes = integer("holdingroomminutes")
    val invitelinkremover = bool("invitelinkremover")
    val welcomemessage = bool("welcomemessage")
    val message = text("message")
    val welcomemessagechannelid = long("welcomemessagechannelid")
    val prefix = text("prefix")
    val silentcommands = bool("silentcommands")
    val nospaceprefix = bool("nospaceprefix")
    val statistics = bool("statistics")
    val joincaptcha = bool("joincaptcha")
}

class JimSettings(id: EntityID<Long>) : Entity<Long>(id) {
    companion object : EntityClass<Long, JimSettings>(JimSettingsTable)

    var modlog by JimSettingsTable.modlog
    var modlogchannelid by JimSettingsTable.modlogchannelid
    var holdingroom by JimSettingsTable.holdingroom
    var holdingroomroleid by JimSettingsTable.holdingroomroleid
    var holdingroomminutes by JimSettingsTable.holdingroomminutes
    var invitelinkremover by JimSettingsTable.invitelinkremover
    var welcomemessage by JimSettingsTable.welcomemessage
    var message by JimSettingsTable.message
    var welcomemessagechannelid by JimSettingsTable.welcomemessagechannelid
    var prefix by JimSettingsTable.prefix
    var silentcommands by JimSettingsTable.silentcommands
    var nospaceprefix by JimSettingsTable.nospaceprefix
    var statistics by JimSettingsTable.statistics
    var joincaptcha by JimSettingsTable.joincaptcha
}
