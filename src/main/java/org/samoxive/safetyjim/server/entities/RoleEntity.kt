package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable
import net.dv8tion.jda.core.entities.Role

@Serializable
data class RoleEntity(
        val id: String,
        val name: String,
        val color: String
)

fun Role.toRoleEntity(): RoleEntity = RoleEntity(id, name, "#${Integer.toHexString(color.rgb).substring(2)}")