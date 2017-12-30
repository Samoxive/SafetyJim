package org.samoxive.safetyjim.server.entries;

import java.util.List;

public class GuildSettings {
    public String id;
    public boolean modLog;
    public PartialChannel modLogChannel;
    public boolean holdingRoom;
    public PartialRole holdingRoomRole;
    public int holdingRoomMinutes;
    public boolean inviteLinkRemover;
    public boolean welcomeMessage;
    public String message;
    public PartialChannel welcomeMessageChannel;
    public String prefix;
    public boolean silentCommands;
    public boolean noSpacePrefix;
    public boolean statistics;
    public List<PartialChannel> channels;
    public List<PartialRole> roles;

    public GuildSettings(String id,
                         boolean modLog,
                         PartialChannel modLogChannel,
                         boolean holdingRoom,
                         PartialRole holdingRoomRole,
                         int holdingRoomMinutes,
                         boolean inviteLinkRemover,
                         boolean welcomeMessage,
                         String message,
                         PartialChannel welcomeMessageChannel,
                         String prefix,
                         boolean silentCommands,
                         boolean noSpacePrefix,
                         boolean statistics,
                         List<PartialChannel> channels,
                         List<PartialRole> roles) {
        this.id = id;
        this.modLog = modLog;
        this.modLogChannel = modLogChannel;
        this.holdingRoom = holdingRoom;
        this.holdingRoomRole = holdingRoomRole;
        this.holdingRoomMinutes = holdingRoomMinutes;
        this.inviteLinkRemover = inviteLinkRemover;
        this.welcomeMessage = welcomeMessage;
        this.message = message;
        this.welcomeMessageChannel = welcomeMessageChannel;
        this.prefix = prefix;
        this.silentCommands = silentCommands;
        this.noSpacePrefix = noSpacePrefix;
        this.statistics = statistics;
        this.channels = channels;
        this.roles = roles;
    }
}
