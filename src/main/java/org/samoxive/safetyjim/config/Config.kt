package org.samoxive.safetyjim.config

import com.uchuhimo.konf.ConfigSpec

object JimConfig : ConfigSpec("jim") {
    val version by required<String>()
    val token by required<String>()
    val default_prefix by required<String>()
    val shard_count by required<Int>()
    val geocode_token by required<String>()
    val darksky_token by required<String>()
}

object DatabaseConfig : ConfigSpec("database") {
    val user by required<String>()
    val pass by required<String>()
    val host by required<String>()
    val port by required<Int>()
    val database by required<String>()
    val jdbc_url by required<String>()
}

object OauthConfig : ConfigSpec("oauth") {
    val client_id by required<String>()
    val client_secret by required<String>()
    val redirect_uri by required<String>()
}

object ServerConfig : ConfigSpec("server") {
    val self_url by required<String>()
    val recaptcha_secret by required<String>()
    val secret by required<String>()
    val port by required<Int>()
}
