package org.samoxive.safetyjim.database

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import net.dv8tion.jda.api.entities.Guild
import org.ahocorasick.trie.Trie
import org.samoxive.safetyjim.config.Config
import org.samoxive.safetyjim.dateFromNow
import org.samoxive.safetyjim.discord.getDefaultChannelTalkable
import org.samoxive.safetyjim.tryhardAsync
import java.util.*
import java.util.concurrent.TimeUnit

private const val createSQL =
    """
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
    modactionconfirmationmessage boolean not null,
    wordfilter boolean not null,
    wordfilterblacklist text,
    wordfilterlevel integer not null,
    wordfilteraction integer not null,
    wordfilteractionduration integer not null,
    wordfilteractiondurationtype integer not null,
    invitelinkremoveraction integer not null,
    invitelinkremoveractionduration integer not null,
    invitelinkremoveractiondurationtype integer not null,
    privacysettings integer not null,
    privacymodlog integer not null,
    softbanthreshold integer not null,
    softbanaction integer not null,
    softbanactionduration integer not null,
    softbanactiondurationtype integer not null,
    kickthreshold integer not null,
    kickaction integer not null,
    kickactionduration integer not null,
    kickactiondurationtype integer not null,
    mutethreshold integer not null,
    muteaction integer not null,
    muteactionduration integer not null,
    muteactiondurationtype integer not null,
    warnthreshold integer not null,
    warnaction integer not null,
    warnactionduration integer not null,
    warnactiondurationtype integer not null,
    modscanedittags boolean not null
);
"""

private const val insertSQL =
    """
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
    modactionconfirmationmessage,
    wordfilter,
    wordfilterblacklist,
    wordfilterlevel,
    wordfilteraction,
    wordfilteractionduration,
    wordfilteractiondurationtype,
    invitelinkremoveraction,
    invitelinkremoveractionduration,
    invitelinkremoveractiondurationtype,
    privacysettings,
    privacymodlog,
    softbanthreshold,
    softbanaction,
    softbanactionduration,
    softbanactiondurationtype,
    kickthreshold,
    kickaction,
    kickactionduration,
    kickactiondurationtype,
    mutethreshold,
    muteaction,
    muteactionduration,
    muteactiondurationtype,
    warnthreshold,
    warnaction,
    warnactionduration,
    warnactiondurationtype,
    modscanedittags
)
values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, $22, $23, $24, $25, $26, $27, $28, $29, $30, $31, $32, $33, $34, $35, $36, $37, $38, $39, $40, $41, $42, $43, $44, $45)
returning *;
"""

private const val updateSQL =
    """
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
    silentcommandslevel = $16,
    modactionconfirmationmessage = $17,
    wordfilter = $18,
    wordfilterblacklist = $19,
    wordfilterlevel = $20,
    wordfilteraction = $21,
    wordfilteractionduration = $22,
    wordfilteractiondurationtype = $23,
    invitelinkremoveraction = $24,
    invitelinkremoveractionduration = $25,
    invitelinkremoveractiondurationtype = $26,
    privacysettings = $27,
    privacymodlog = $28,
    softbanthreshold = $29,
    softbanaction = $30,
    softbanactionduration = $31,
    softbanactiondurationtype = $32,
    kickthreshold = $33,
    kickaction = $34,
    kickactionduration = $35,
    kickactiondurationtype = $36,
    mutethreshold = $37,
    muteaction = $38,
    muteactionduration = $39,
    muteactiondurationtype = $40,
    warnthreshold = $41,
    warnaction = $42,
    warnactionduration = $43,
    warnactiondurationtype = $44,
    modscanedittags = $45
where guildid = $1;
"""

object SettingsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private val settingsCache: Cache<Long, SettingsEntity> = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build()

    private val wordFilterCache: Cache<Long, Trie> = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build()

    private fun RowSet<Row>.toSettingsEntities(): List<SettingsEntity> = this.map {
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
            modActionConfirmationMessage = it.getBoolean(16),
            wordFilter = it.getBoolean(17),
            wordFilterBlacklist = it.getString(18),
            wordFilterLevel = it.getInteger(19),
            wordFilterAction = it.getInteger(20),
            wordFilterActionDuration = it.getInteger(21),
            wordFilterActionDurationType = it.getInteger(22),
            inviteLinkRemoverAction = it.getInteger(23),
            inviteLinkRemoverActionDuration = it.getInteger(24),
            inviteLinkRemoverActionDurationType = it.getInteger(25),
            privacySettings = it.getInteger(26),
            privacyModLog = it.getInteger(27),
            softbanThreshold = it.getInteger(28),
            softbanAction = it.getInteger(29),
            softbanActionDuration = it.getInteger(30),
            softbanActionDurationType = it.getInteger(31),
            kickThreshold = it.getInteger(32),
            kickAction = it.getInteger(33),
            kickActionDuration = it.getInteger(34),
            kickActionDurationType = it.getInteger(35),
            muteThreshold = it.getInteger(36),
            muteAction = it.getInteger(37),
            muteActionDuration = it.getInteger(38),
            muteActionDurationType = it.getInteger(39),
            warnThreshold = it.getInteger(40),
            warnAction = it.getInteger(41),
            warnActionDuration = it.getInteger(42),
            warnActionDurationType = it.getInteger(43),
            modsCanEditTags = it.getBoolean(44)
        )
    }

    suspend fun getGuildSettings(guild: Guild, config: Config): SettingsEntity {
        val cachedSetting = settingsCache.getIfPresent(guild.idLong)
        if (cachedSetting != null) {
            return cachedSetting
        }

        val existingSetting = fetchGuildSettings(guild)
        if (existingSetting != null) {
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
                    prefix = config.jim.default_prefix
                )
            )
        }
    }

    private suspend fun insertSettings(settings: SettingsEntity): SettingsEntity {
        val updatedSettings = pgPool.preparedQueryAwait(insertSQL, settings.toTuple())
            .toSettingsEntities()
            .first()
        settingsCache.put(settings.guildId, settings)
        wordFilterCache.invalidate(settings.guildId)
        return updatedSettings
    }

    suspend fun updateSettings(newSettings: SettingsEntity) {
        pgPool.preparedQueryAwait(updateSQL, newSettings.toTuple())
        settingsCache.put(newSettings.guildId, newSettings)
        wordFilterCache.invalidate(newSettings.guildId)
    }

    suspend fun deleteSettings(guild: Guild) {
        pgPool.preparedQueryAwait("delete from settings where guildid = $1;", Tuple.of(guild.idLong))
        settingsCache.invalidate(guild.idLong)
        wordFilterCache.invalidate(guild.idLong)
    }

    suspend fun resetSettings(guild: Guild, config: Config) {
        deleteSettings(guild)
        insertDefaultGuildSettings(config, guild)
    }

    fun getWordFilter(settings: SettingsEntity): Trie {
        check(settings.wordFilter)
        checkNotNull(settings.wordFilterBlacklist)

        val cachedFilter = wordFilterCache.getIfPresent(settings.guildId)
        if (cachedFilter != null) {
            return cachedFilter
        }

        val newFilterBuilder = Trie.builder()
            .addKeywords(
                settings.wordFilterBlacklist.split(",")
                    .map { it.lowercase().trim() }
                    .filter { it.isNotEmpty() }
            )
            .ignoreCase()
            .stopOnHit()

        val newFilter = if (settings.wordFilterLevel == SettingsEntity.WORD_FILTER_LEVEL_LOW) {
            newFilterBuilder.onlyWholeWords().build()
        } else {
            newFilterBuilder.build()
        }

        wordFilterCache.put(settings.guildId, newFilter)
        return newFilter
    }
}

private const val DEFAULT_WELCOME_MESSAGE = "Welcome to \$guild \$user!"

@Throws(IllegalArgumentException::class)
fun getDelta(durationType: Int, duration: Int): Int {
    val delta: Long = when (durationType) {
        SettingsEntity.DURATION_TYPE_SECONDS -> duration * 1L
        SettingsEntity.DURATION_TYPE_MINUTES -> duration * 60L
        SettingsEntity.DURATION_TYPE_HOURS -> duration * 60L * 60L
        SettingsEntity.DURATION_TYPE_DAYS -> duration * 60L * 60L * 24L
        else -> throw IllegalStateException()
    }

    require(delta >= 0) { "Mod action duration cannot be negative!" }

    // don't let users set too big of a time delta, will Jim even be alive then?
    require(delta <= (3L * 365L * 24L * 60L * 60L)) { "Mod action duration is too long! (more than 3 years)" }

    // can't overflow, we did our homework above
    return delta.toInt()
}

fun getExpirationDateOfModAction(action: Int, durationType: Int, duration: Int): Date? {
    return if (action == SettingsEntity.ACTION_BAN || action == SettingsEntity.ACTION_MUTE) {
        if (duration == 0) {
            null
        } else {
            dateFromNow(getDelta(durationType, duration))
        }
    } else {
        null
    }
}

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
    val silentCommandsLevel: Int = SILENT_COMMANDS_MOD_ONLY,
    val modActionConfirmationMessage: Boolean = true,
    val wordFilter: Boolean = false,
    val wordFilterBlacklist: String? = null,
    val wordFilterLevel: Int = WORD_FILTER_LEVEL_LOW,
    val wordFilterAction: Int = ACTION_WARN,
    val wordFilterActionDuration: Int = 0,
    val wordFilterActionDurationType: Int = DURATION_TYPE_MINUTES,
    val inviteLinkRemoverAction: Int = ACTION_WARN,
    val inviteLinkRemoverActionDuration: Int = 0,
    val inviteLinkRemoverActionDurationType: Int = DURATION_TYPE_MINUTES,
    val privacySettings: Int = PRIVACY_EVERYONE,
    val privacyModLog: Int = PRIVACY_EVERYONE,
    val softbanThreshold: Int = 0,
    val softbanAction: Int = ACTION_NOTHING,
    val softbanActionDuration: Int = 0,
    val softbanActionDurationType: Int = DURATION_TYPE_MINUTES,
    val kickThreshold: Int = 0,
    val kickAction: Int = ACTION_NOTHING,
    val kickActionDuration: Int = 0,
    val kickActionDurationType: Int = DURATION_TYPE_MINUTES,
    val muteThreshold: Int = 0,
    val muteAction: Int = ACTION_NOTHING,
    val muteActionDuration: Int = 0,
    val muteActionDurationType: Int = DURATION_TYPE_MINUTES,
    val warnThreshold: Int = 0,
    val warnAction: Int = ACTION_NOTHING,
    val warnActionDuration: Int = 0,
    val warnActionDurationType: Int = DURATION_TYPE_MINUTES,
    val modsCanEditTags: Boolean = false
) {
    companion object {
        const val SILENT_COMMANDS_MOD_ONLY = 0
        const val SILENT_COMMANDS_ALL = 1
        const val WORD_FILTER_LEVEL_LOW = 0
        const val WORD_FILTER_LEVEL_HIGH = 1
        const val ACTION_NOTHING = 0
        const val ACTION_WARN = 1
        const val ACTION_MUTE = 2
        const val ACTION_KICK = 3
        const val ACTION_BAN = 4
        const val ACTION_SOFTBAN = 5
        const val ACTION_HARDBAN = 6
        const val DURATION_TYPE_SECONDS = 0
        const val DURATION_TYPE_MINUTES = 1
        const val DURATION_TYPE_HOURS = 2
        const val DURATION_TYPE_DAYS = 3
        const val PRIVACY_EVERYONE = 0
        const val PRIVACY_STAFF_ONLY = 1
        const val PRIVACY_ADMIN_ONLY = 2
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
            modActionConfirmationMessage,
            wordFilter,
            wordFilterBlacklist,
            wordFilterLevel,
            wordFilterAction,
            wordFilterActionDuration,
            wordFilterActionDurationType,
            inviteLinkRemoverAction,
            inviteLinkRemoverActionDuration,
            inviteLinkRemoverActionDurationType,
            privacySettings,
            privacyModLog,
            softbanThreshold,
            softbanAction,
            softbanActionDuration,
            softbanActionDurationType,
            kickThreshold,
            kickAction,
            kickActionDuration,
            kickActionDurationType,
            muteThreshold,
            muteAction,
            muteActionDuration,
            muteActionDurationType,
            warnThreshold,
            warnAction,
            warnActionDuration,
            warnActionDurationType,
            modsCanEditTags
        )
    }

    fun getWordFilterActionExpirationDate(): Date? = getExpirationDateOfModAction(wordFilterAction, wordFilterActionDurationType, wordFilterActionDuration)
    fun getInviteLinkRemoverActionExpirationDate(): Date? = getExpirationDateOfModAction(inviteLinkRemoverAction, inviteLinkRemoverActionDurationType, inviteLinkRemoverActionDuration)
    fun getSoftbanActionExpirationDate(): Date? = getExpirationDateOfModAction(softbanAction, softbanActionDurationType, softbanActionDuration)
    fun getKickActionExpirationDate(): Date? = getExpirationDateOfModAction(kickAction, kickActionDurationType, kickActionDuration)
    fun getMuteActionExpirationDate(): Date? = getExpirationDateOfModAction(muteAction, muteActionDurationType, muteActionDuration)
    fun getWarnActionExpirationDate(): Date? = getExpirationDateOfModAction(warnAction, warnActionDurationType, warnActionDuration)
}
