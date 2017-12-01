package org.samoxive.safetyjim.database;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;

import java.util.HashMap;
import java.util.Map;

public class DatabaseUtils {
    public static final String DEFAULT_WELCOME_MESSAGE = "Welcome to $guild $user!";

    public static SettingsRecord getGuildSettings(DSLContext database, Guild guild) {
        return database.selectFrom(Tables.SETTINGS)
                       .where(Tables.SETTINGS.GUILDID.eq(guild.getId()))
                       .fetchAny();
    }

    public static void deleteGuildSettings(DSLContext database, Guild guild) {
        database.deleteFrom(Tables.SETTINGS).where(Tables.SETTINGS.GUILDID.eq(guild.getId())).execute();
    }

    public static void createGuildSettings(DiscordBot bot, DSLContext database, Guild guild) {
        SettingsRecord record = database.newRecord(Tables.SETTINGS);

        record.setGuildid(guild.getId());
        record.setSilentcommands(false);
        record.setInvitelinkremover(false);
        record.setModlog(false);
        record.setModlogchannelid(DiscordUtils.getDefaultChannel(guild).getId());
        record.setHoldingroom(false);
        record.setHoldingroomroleid(null);
        record.setHoldingroomminutes(3);
        record.setPrefix(bot.getConfig().jim.default_prefix);
        record.setWelcomemessage(false);
        record.setMessage(DEFAULT_WELCOME_MESSAGE);
        record.setWelcomemessagechannelid(DiscordUtils.getDefaultChannel(guild).getId());
    }
}
