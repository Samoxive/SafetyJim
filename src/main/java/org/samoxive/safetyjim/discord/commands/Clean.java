package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.helpers.Pair;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class Clean extends Command {
    private String[] usages = { "clean <number> - deletes last number of messages",
                                "clean <number> @user - deletes number of messages from specified user",
                                "clean <number> bot - deletes number of messages sent from bots" };

    private List<Message> fetchMessages(TextChannel channel, int messageCount, boolean skipOneMessage, boolean filterBotMessages, boolean filterUserMessages, User filterUser) {
        if (skipOneMessage) {
            messageCount = messageCount == 100 ? 100 : messageCount + 1;
        }

        // if we want to delete bot messages, we want to find as much as we can and then only delete the amount we need
        // if not, we just pass the messageCount back, same story with user messages
        List<Message> messages = channel.getHistory().retrievePast((filterBotMessages || filterUserMessages) ? 100 : messageCount).complete();

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

        if (filterUserMessages) {
            List<Message> tempMessages = new ArrayList<>();
            messageCount--;
            int iterationCount = 0;

            for (Message message: messages) {
                if (iterationCount == messageCount) {
                    break;
                } else {
                    if (message.getAuthor().getId().equals(filterUser.getId())) {
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
        List<AuditableRestAction<Void>> futures = new ArrayList<>();

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

        for (AuditableRestAction<Void> future: futures) {
            future.complete();
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

        if (!member.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Manage Messages");
            return false;
        }

        if (!selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that! Required permission: Manage Messages, Read Message History");
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
            List<User> mentionedUsers = message.getMentionedUsers();
            if (mentionedUsers.isEmpty()) {
                DiscordUtils.failMessage(bot, message, "Could not find the user to clean messages of!");
                return false;
            }
            targetUser = mentionedUsers.get(0);
            targetArgument = "user";
        } else {
            targetArgument = messageIterator.next();
        }

        List<Message> messages;
        switch (targetArgument) {
            case "":
                messages = fetchMessages(channel, messageCount, true, false, false, null);
                break;
            case "bot":
                messages = fetchMessages(channel, messageCount, false, true, false, null);
                break;
            case "user":
                if (targetUser != null) {
                    messages = fetchMessages(channel, messageCount, true, false, true, targetUser);
                    break;
                }
            default:
                DiscordUtils.failMessage(bot, message, "Invalid target, please try mentioning a user or writing `bot`.");
                return false;
        }

        Pair<List<Message>, List<Message>> seperatedMessages = seperateMessages(messages);
        try {
            bulkDelete(seperatedMessages, channel);
        } catch (Exception e) {
            //
        }

        DiscordUtils.successReact(bot, message);

        return false;
    }
}
