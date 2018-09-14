package org.samoxive.safetyjim.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable

object OauthSecretTable: IdTable<String>(name = "ouathsecrets") {
    override val id = text("userid").primaryKey().entityId()
    val access_token = text("access_token")
    val expiration_time =  long("expiration_time")
    val refresh_token =  text("refresh_token")
}

class OauthSecret(id: EntityID<String>): Entity<String>(id) {
    companion object : EntityClass<String, OauthSecret>(OauthSecretTable)

    var access_token by OauthSecretTable.access_token
    var expiration_time by OauthSecretTable.expiration_time
    var refresh_token by OauthSecretTable.refresh_token
}