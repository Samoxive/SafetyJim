package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Role
import java.awt.Color

@Serializable
data class RoleEntity(
    val id: String,
    val name: String,
    val color: String
)

private fun Int.toHex() = Integer.toHexString(this)

private fun intToHex(c: Color): String = "#${c.red.toHex()}${c.green.toHex()}${c.blue.toHex()}"

fun Role.toRoleEntity(): RoleEntity = RoleEntity(id, name, intToHex(color ?: Color.WHITE))