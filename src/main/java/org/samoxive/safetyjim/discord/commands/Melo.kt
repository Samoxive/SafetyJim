package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.JimSettings
import org.samoxive.safetyjim.discord.*

class Melo : Command() {
    override val usages = arrayOf("melo - \uD83C\uDF48")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: JimSettings, args: String): Boolean {
        event.message.meloReact()
        return false
    }
}
