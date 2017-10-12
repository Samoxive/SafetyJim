package org.samoxive.safetyjim.discord.database;

import net.dv8tion.jda.core.entities.Guild;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.Settings;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;

import java.util.HashMap;
import java.util.Map;

public class DatabaseUtils {
    public static final String[] possibleSettingKeys = {
            "modlogactive",
            "modlogchannelid",
            "holdingroomroleid",
            "holdingroomactive",
            "invitelinkremover",
            "holdingroomminutes",
            "welcomemessagechannelid",
            "prefix",
            "welcomemessage",
            "welcomemessageactive",
            "silentcommands"
    };
    private static final String DEFAULT_WELCOME_MESSAGE = "Welcome to $guild $user!";

    public static Map<String, String> getGuildSettings(DSLContext database, Guild guild) {
        Map<String, String> settings = new HashMap<>();
        SettingsRecord[] settingsRecords = database.selectFrom(Tables.SETTINGS)
                                                   .where(Tables.SETTINGS.GUILDID.eq(guild.getId()))
                                                   .fetchArray();
        for (SettingsRecord record: settingsRecords) {
            settings.put(record.getKey(), record.getValue());
        }

        return settings;
    }

    public static void deleteGuildSettings(DSLContext database, Guild guild) {
        database.deleteFrom(Tables.SETTINGS).where(Tables.SETTINGS.GUILDID.eq(guild.getId())).execute();
    }

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
