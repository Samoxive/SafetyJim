package org.samoxive.safetyjim.database

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.uchuhimo.konf.Config
import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple
import net.dv8tion.jda.core.entities.Guild
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.discord.getDefaultChannelTalkable
import org.samoxive.safetyjim.tryhardAsync
import java.util.concurrent.TimeUnit

private const val createSQL = """
create table if not exists settings (
    guildid bigint not null primary key,
    modlog boolean not null,
    modlogchannelid bigint not null,
    holdingroom boolean not null,
    holdingroomroleid bigint,
    holdingroomminutes integer not null,
    invitelinkremover boolean not null,
    welcomemessage boolean not null,
    message text not null,
    welcomemessagechannelid bigint not null,
    prefix text not null,
    silentcommands boolean not null,
    nospaceprefix boolean not null,
    statistics boolean not null,
    joincaptcha boolean not null,
    silentcommandslevel integer not null,
    modactionconfirmationmessage boolean not null
);
"""

private const val insertSQL = """
insert into settings (
    guildid,
    modlog,
    modlogchannelid,
    holdingroom,
    holdingroomroleid,
    holdingroomminutes,
    invitelinkremover,
    welcomemessage,
    message,
    welcomemessagechannelid,
    prefix,
    silentcommands,
    nospaceprefix,
    "statistics",
    joincaptcha,
    silentcommandslevel,
    modactionconfirmationmessage
)
values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17)
returning *;
"""

private const val updateSQL = """
update settings set
    modlog = $2,
    modlogchannelid = $3,
    holdingroom = $4,
    holdingroomroleid = $5,
    holdingroomminutes = $6,
    invitelinkremover = $7,
    welcomemessage = $8,
    message = $9,
    welcomemessagechannelid = $10,
    prefix = $11,
    silentcommands = $12,
    nospaceprefix = $13,
    "statistics" = $14,
    joincaptcha = $15,
    silentcommandslevel = $16
    modactionconfirmationmessage = $17
where guildid = $1;
"""

object SettingsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private val settingsCache: Cache<Long, SettingsEntity> = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build<Long, SettingsEntity>()

    private fun PgRowSet.toSettingsEntities(): List<SettingsEntity> = this.map {
        SettingsEntity(
                guildId = it.getLong(0),
                modLog = it.getBoolean(1),
                modLogChannelId = it.getLong(2),
                holdingRoom = it.getBoolean(3),
                holdingRoomRoleId = it.getLong(4),
                holdingRoomMinutes = it.getInteger(5),
                inviteLinkRemover = it.getBoolean(6),
                welcomeMessage = it.getBoolean(7),
                message = it.getString(8),
                welcomeMessageChannelId = it.getLong(9),
                prefix = it.getString(10),
                silentCommands = it.getBoolean(11),
                noSpacePrefix = it.getBoolean(12),
                statistics = it.getBoolean(13),
                joinCaptcha = it.getBoolean(14),
                silentCommandsLevel = it.getInteger(15),
                modActionConfirmationMessage = it.getBoolean(16)
        )
    }

    suspend fun getGuildSettings(guild: Guild, config: Config): SettingsEntity {
        val cachedSetting = settingsCache.getIfPresent(guild.idLong)
        if (cachedSetting != null) {
            return cachedSetting
        }

        val existingSetting = fetchGuildSettings(guild)
        if (existingSetting != null) {
            String
            settingsCache.put(guild.idLong, existingSetting)
            return existingSetting
        }

        val newSetting = insertDefaultGuildSettings(config, guild) ?: fetchGuildSettings(guild)!!
        settingsCache.put(guild.idLong, newSetting)
        return newSetting
    }

    private suspend fun fetchGuildSettings(guild: Guild): SettingsEntity? {
        return pgPool.preparedQueryAwait("select * from settings where guildid = $1;", Tuple.of(guild.idLong))
                .toSettingsEntities()
                .firstOrNull()
    }


    suspend fun insertDefaultGuildSettings(config: Config, guild: Guild): SettingsEntity? {
        val defaultChannel = guild.getDefaultChannelTalkable()
        return tryhardAsync {
            insertSettings(
                    SettingsEntity(
                            guildId = guild.idLong,
                            modLogChannelId = defaultChannel.idLong,
                            welcomeMessageChannelId = defaultChannel.idLong,
                            prefix = config[JimConfig.default_prefix]
                    )
            )
        }
    }

    private suspend fun insertSettings(settings: SettingsEntity): SettingsEntity {
        val updatedSettings = pgPool.preparedQueryAwait(insertSQL, settings.toTuple())
                .toSettingsEntities()
                .first()
        settingsCache.put(settings.guildId, settings)
        return updatedSettings
    }

    suspend fun updateSettings(newSettings: SettingsEntity) {
        pgPool.preparedQueryAwait(updateSQL, newSettings.toTuple())
        settingsCache.put(newSettings.guildId, newSettings)
    }

    suspend fun deleteSettings(guild: Guild) {
        pgPool.preparedQueryAwait("delete from settings where guildid = $1;", Tuple.of(guild.idLong))
        settingsCache.invalidate(guild.idLong)
    }

    suspend fun resetSettings(guild: Guild, config: Config) {
        deleteSettings(guild)
        insertDefaultGuildSettings(config, guild)
    }
}

private const val DEFAULT_WELCOME_MESSAGE = "Welcome to \$guild \$user!"

data class SettingsEntity(
        val guildId: Long,
        val modLog: Boolean = false,
        val modLogChannelId: Long,
        val holdingRoom: Boolean = false,
        val holdingRoomRoleId: Long? = null,
        val holdingRoomMinutes: Int = 3,
        val inviteLinkRemover: Boolean = false,
        val welcomeMessage: Boolean = false,
        val message: String = DEFAULT_WELCOME_MESSAGE,
        val welcomeMessageChannelId: Long,
        val prefix: String,
        val silentCommands: Boolean = false,
        val noSpacePrefix: Boolean = false,
        val statistics: Boolean = false,
        val joinCaptcha: Boolean = false,
        val silentCommandsLevel: Int = 0,
        val modActionConfirmationMessage: Boolean = true
) {
    companion object {
        const val MOD_COMMANDS_ONLY = 0
        const val ALL = 1
    }

    fun toTuple(): Tuple {
        return Tuple.of(
                guildId,
                modLog,
                modLogChannelId,
                holdingRoom,
                holdingRoomRoleId,
                holdingRoomMinutes,
                inviteLinkRemover,
                welcomeMessage,
                message,
                welcomeMessageChannelId,
                prefix,
                silentCommands,
                noSpacePrefix,
                statistics,
                joinCaptcha,
                silentCommandsLevel,
                modActionConfirmationMessage
        )
    }
}