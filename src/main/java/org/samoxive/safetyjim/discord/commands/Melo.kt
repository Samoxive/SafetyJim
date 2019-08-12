package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.meloReact

class Melo : Command() {
    override val usages = arrayOf("melo - \uD83C\uDF48")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        event.message.meloReact()
        return false
    }
}
