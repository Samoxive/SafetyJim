package org.samoxive.safetyjim.config

data class Config(
    val jim: JimConfig,
    val database: DatabaseConfig,
    val oauth: OauthConfig,
    val server: ServerConfig
)

data class JimConfig(
    val version: String,
    val token: String,
    val default_prefix: String,
    val shard_count: Int,
    val geocode_token: String,
    val darksky_token: String,
    val metrics: Boolean
)

data class DatabaseConfig(
    val user: String,
    val pass: String,
    val host: String,
    val port: Int,
    val database: String
)

data class OauthConfig(
    val client_id: String,
    val client_secret: String,
    val redirect_uri: String
)

data class ServerConfig(
    val self_url: String,
    val recaptcha_secret: String,
    val secret: String,
    val port: Int
)
