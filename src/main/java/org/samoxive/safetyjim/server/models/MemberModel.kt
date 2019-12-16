package org.samoxive.safetyjim.server.models

import net.dv8tion.jda.api.entities.Member

data class MemberModel(
    val id: String,
    val nickname: String?,
    val username: String,
    val avatarUrl: String
)

fun Member.toMemberModel(): MemberModel = MemberModel(id, nickname, user.name, user.effectiveAvatarUrl)
