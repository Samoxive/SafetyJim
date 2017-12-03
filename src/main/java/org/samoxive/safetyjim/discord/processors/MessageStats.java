package org.samoxive.safetyjim.discord.processors;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.MessagesRecord;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordShard;
import org.samoxive.safetyjim.discord.MessageProcessor;

public class MessageStats extends MessageProcessor {
    @Override
    public boolean onMessage(DiscordBot bot, DiscordShard shard, GuildMessageReceivedEvent event) {
        shard.getThreadPool().submit(() -> {
            DSLContext database = bot.getDatabase();

            Guild guild = event.getGuild();
            SettingsRecord guildSettings = DatabaseUtils.getGuildSettings(database, guild);
            if (!guildSettings.getStatistics()) {
                return;
            }

            Message message = event.getMessage();
            TextChannel channel = event.getChannel();
            String content = message.getRawContent();
            User user = event.getMember().getUser();
            int wordCount = content.split(" ").length;
            MessagesRecord record = database.newRecord(Tables.MESSAGES);

            record.setMessageid(message.getId());
            record.setUserid(user.getId());
            record.setChannelid(channel.getId());
            record.setGuildid(guild.getId());
            record.setDate(message.getCreationTime().toEpochSecond());
            record.setWordcount(wordCount);
            record.store();
        });

        return false;
    }

    @Override
    public void onMessageDelete(DiscordBot bot, DiscordShard shard, GuildMessageDeleteEvent event) {
        DSLContext database = bot.getDatabase();
        String messageId = event.getMessageId();
        database.deleteFrom(Tables.MESSAGES)
                .where(Tables.MESSAGES.MESSAGEID.eq(messageId))
                .execute();
    }
}
