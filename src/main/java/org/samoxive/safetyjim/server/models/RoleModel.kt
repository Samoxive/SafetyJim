package org.samoxive.safetyjim.server.models

import net.dv8tion.jda.api.entities.Role
import java.awt.Color

data class RoleModel(
    val id: String,
    val name: String,
    val color: String
)

private fun Int.toHex() = Integer.toHexString(this)

private fun intToHex(c: Color): String = "#${c.red.toHex()}${c.green.toHex()}${c.blue.toHex()}"

fun Role.toRoleModel(): RoleModel = RoleModel(id, name, intToHex(color ?: Color.WHITE))
