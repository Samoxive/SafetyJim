package org.samoxive.safetyjim.config

import com.uchuhimo.konf.ConfigSpec

object JimConfig : ConfigSpec("jim") {
    val version by required<String>()
    val token by required<String>()
    val default_prefix by required<String>()
    val shard_count by required<Int>()
}

object DatabaseConfig : ConfigSpec("database") {
    val user by required<String>()
    val pass by required<String>()
    val jdbc_url by required<String>()
}

object BotListConfig : ConfigSpec("botlist") {
    val enabled by required<Boolean>()
    val list by required<List<list>>()
}

data class list(
    val name: String,
    val url: String,
    val token: String,
    val ignore_errors: Boolean
)

object OauthConfig : ConfigSpec("oauth") {
    val client_id by required<String>()
    val client_secret by required<String>()
    val redirect_uri by required<String>()
}

object ServerConfig : ConfigSpec("server") {
    val secret by required<String>()
    val base_url by required<String>()
    val port by required<Int>()
}
