package org.samoxive.safetyjim.server.entities;

import java.util.List;

public class GuildSettings {
    public String id;
    public Boolean modLog;
    public PartialChannel modLogChannel;
    public Boolean holdingRoom;
    public PartialRole holdingRoomRole;
    public Integer holdingRoomMinutes;
    public Boolean inviteLinkRemover;
    public Boolean welcomeMessage;
    public String message;
    public PartialChannel welcomeMessageChannel;
    public String prefix;
    public Boolean silentCommands;
    public Boolean noSpacePrefix;
    public Boolean statistics;
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
