package org.samoxive.safetyjim.server.models

data class AccessTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String,
    val scope: String
)
