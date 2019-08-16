package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.User

@Serializable
data class UserModel(
    val id: String,
    val username: String,
    val avatarUrl: String
)

fun User.toUserModel(): UserModel = UserModel(id, name, effectiveAvatarUrl)