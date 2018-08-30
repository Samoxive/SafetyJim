package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable

object JimSettingsTable : IdTable<String>(name = "settings") {
    override val id = text("guildid").primaryKey().entityId()
    val modlog = bool("modlog")
    val modlogchannelid = text("modlogchannelid")
    val holdingroom = bool("holdingroom")
    val holdingroomroleid = text("holdingroomroleid").nullable()
    val holdingroomminutes = integer("holdingroomminutes")
    val invitelinkremover = bool("invitelinkremover")
    val welcomemessage = bool("welcomemessage")
    val message = text("message")
    val welcomemessagechannelid = text("welcomemessagechannelid")
    val prefix = text("prefix")
    val silentcommands = bool("silentcommands")
    val nospaceprefix = bool("nospaceprefix")
    val statistics = bool("statistics")
}

class JimSettings(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, JimSettings>(JimSettingsTable)

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
}
