package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.requests.RequestFuture;
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.MessageProcessor;
import org.samoxive.safetyjim.helpers.Pair;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Clean extends Command {
    private String[] usages = { "clean <number> - deletes last number of messages",
                                "clean <number> @user - deletes number of messages from specified user",
                                "clean <number> bot - deletes number of messages sent from bots" };

    /**
     *
     * @param channel
     * @param messageCount amount of messages you want to delete
     * @param skipOneMessage
     * @param filterBotMessages
     * @return
     */
    private List<Message> fetchMessages(TextChannel channel, int messageCount, boolean skipOneMessage, boolean filterBotMessages) {
        if (skipOneMessage) {
            messageCount = messageCount == 100 ? 100 : messageCount + 1;
        }

        // if we want to delete bot messages, we want to find as much as we can and then only delete the amount we need
        // if not, we just pass the messageCount back
        List<Message> messages = channel.getHistory().retrievePast(filterBotMessages ? 100 : messageCount).complete();

        if (skipOneMessage) {
            try {
                messages.remove(0);
            } catch (IndexOutOfBoundsException e) {
                // we just want to remove first element, ignore if list is empty
            }
        }

        if (filterBotMessages) {
            List<Message> tempMessages = new ArrayList<>();
            int iterationCount = 0;

            for (Message message: messages) {
                if (iterationCount == messageCount) {
                    break;
                } else {
                    if (message.getAuthor().isBot()) {
                        tempMessages.add(message);
                        iterationCount++;
                    }
                }
            }

            messages = tempMessages;
        }

        return messages;
    }

    private Pair<List<Message>, List<Message>> seperateMessages(List<Message> messages) {
        List<Message> oldMessages = new ArrayList<>();
        List<Message> newMessages = new ArrayList<>();
        long now = (new Date()).getTime() / 1000;

        for (Message message: messages) {
            if ((now - message.getCreationTime().toEpochSecond()) <= 60 * 60 * 24 * 12) {
                newMessages.add(message);
            } else {
                oldMessages.add(message);
            }
        }

        return new Pair<>(oldMessages, newMessages);
    }

    private void bulkDelete(Pair<List<Message>, List<Message>> messages, TextChannel channel) {
        List<Message> newMessages = messages.getRight();
        List<Message> oldMessage = messages.getLeft();
        List<AuditableRestAction> futures = new ArrayList<>();

        if (newMessages.size() >= 2 && newMessages.size() <= 100) {
            channel.deleteMessages(newMessages).complete();
        } else {
            for (Message message: newMessages) {
                futures.add(message.delete());
            }
        }

        for (Message message: oldMessage) {
            futures.add(message.delete());
        }

        for (AuditableRestAction future: futures) {
            try {
                future.complete();
            } catch (Exception e) {
                //
            }
        }
    }

    

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        Scanner messageIterator = new Scanner(args);

        Member member = event.getMember();
        Message message = event.getMessage();
        TextChannel channel = event.getChannel();
        Guild guild = event.getGuild();
        Member selfMember = guild.getSelfMember();

        if (!member.hasPermission(Permission.MESSAGE_MANAGE)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command!");
            return false;
        }

        if (!selfMember.hasPermission(Permission.MESSAGE_MANAGE)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!");
            return false;
        }

        if (!messageIterator.hasNextInt()) {
            DiscordUtils.failReact(bot, message);
            return true;
        }

        int messageCount = messageIterator.nextInt();

        if (messageCount < 1) {
            DiscordUtils.failMessage(bot, message, "You can't delete zero or negative messages.");
            return false;
        } else if (messageCount > 100) {
            DiscordUtils.failMessage(bot, message, "You can't delete more than 100 messages at once.");
            return false;
        }

        String targetArgument;
        User targetUser = null;

        if (!messageIterator.hasNext()) {
            targetArgument = "";
        } else if (messageIterator.hasNext(DiscordUtils.USER_MENTION_PATTERN)) {
            targetUser = message.getMentionedUsers().get(0);
            targetArgument = "user";
        } else {
            targetArgument = messageIterator.next();
        }

        List<Message> messages;
        switch (targetArgument) {
            case "":
                messages = fetchMessages(channel, messageCount, true, false);
                break;
            case "bot":
                messages = fetchMessages(channel, messageCount, false, true);
                break;
            case "user":
                if (targetUser != null) {
                    messages = fetchMessages(channel, messageCount, true, false);
                    break;
                }
            default:
                DiscordUtils.failMessage(bot, message, "Invalid target, please try mentioning a user or writing `bot`.");
                return false;
        }

        Pair<List<Message>, List<Message>> seperatedMessages = seperateMessages(messages);
        bulkDelete(seperatedMessages, channel);
        DiscordUtils.successReact(bot, message);

        return false;
    }
}
