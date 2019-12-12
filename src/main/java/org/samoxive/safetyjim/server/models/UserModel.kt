package org.samoxive.safetyjim.server.models

import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.discord.getTag

data class UserModel(
    val id: String,
    val username: String,
    val avatarUrl: String
)

fun User.toUserModel(): UserModel = UserModel(id, getTag(), effectiveAvatarUrl)
