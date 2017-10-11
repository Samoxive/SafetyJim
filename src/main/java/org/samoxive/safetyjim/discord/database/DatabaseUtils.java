package org.samoxive.safetyjim.discord.database;

import net.dv8tion.jda.core.entities.Guild;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;

public class DatabaseUtils {
    private static final String DEFAULT_WELCOME_MESSAGE = "Welcome to $guild $user!";
    public static void createGuildSettings(DiscordBot bot, DSLContext database, Guild guild) {
        createKeyValueSetting(database, guild, "silentcommands", "false");
        createKeyValueSetting(database, guild, "invitelinkremover", "false");
        createKeyValueSetting(database, guild, "modlogactive", "false");
        createKeyValueSetting(database, guild, "modlogchannelid", DiscordUtils.getDefaultChannel(guild).getId());
        createKeyValueSetting(database, guild, "holdingroomroleid", null);
        createKeyValueSetting(database, guild, "holdingroomactive", "false");
        createKeyValueSetting(database, guild, "holdingroomminutes", "3");
        createKeyValueSetting(database, guild, "prefix", bot.getConfig().jim.default_prefix);
        createKeyValueSetting(database, guild, "welcomemessageactive", "false");
        createKeyValueSetting(database, guild, "welcomemessage", DEFAULT_WELCOME_MESSAGE);
        createKeyValueSetting(database, guild, "welcomemessagechannelid", DiscordUtils.getDefaultChannel(guild).getId());
    }

    public static void createKeyValueSetting(DSLContext database, Guild guild, String key, String value) {
        SettingsRecord newRecord = database.newRecord(Tables.SETTINGS);

        newRecord.setGuildid(guild.getId());
        newRecord.setKey(key);
        newRecord.setValue(value);

        newRecord.store();
    }
}
