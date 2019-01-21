package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object JimReminderTable : IntIdTable(name = "reminderlist") {
    val userid = long("userid")
    val channelid = long("channelid")
    val guildid = long("guildid")
    val createtime = long("createtime")
    val remindtime = long("remindtime")
    val reminded = bool("reminded")
    val message = text("message")
}

class JimReminder(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<JimReminder>(JimReminderTable)

    var userid by JimReminderTable.userid
    var channelid by JimReminderTable.channelid
    var guildid by JimReminderTable.guildid
    var createtime by JimReminderTable.createtime
    var remindtime by JimReminderTable.remindtime
    var reminded by JimReminderTable.reminded
    var message by JimReminderTable.message
}