package safetyjim

import (
	"SafetyJim/log"
	"fmt"
	"strings"

	"github.com/bwmarrin/discordgo"
)

// ReadyHandler handles when the bot is initialized.
func (bot *DiscordBot) ReadyHandler(s *discordgo.Session, r *discordgo.Ready) {
	log.Info(fmt.Sprintf("Shard %d ready", s.ShardID))
}

// MessageCreateHandler handles when a message is sent
func (bot *DiscordBot) MessageCreateHandler(s *discordgo.Session, m *discordgo.MessageCreate) {
	if m.Author.Bot {
		return
	}

	channel, err := s.Channel(m.ChannelID)
	if err != nil || channel.Type != discordgo.ChannelTypeGuildText {
		return
	}

	splitMessage := strings.Split(m.Content, " ")
	prefix := splitMessage[0]

	if prefix != "-mod" {
		bot.SuccessReact(m.Message)
	}
}

// MessageDeleteHandler handles when a message is deleted
func (bot *DiscordBot) MessageDeleteHandler(s *discordgo.Session, m *discordgo.MessageDelete) {
	fmt.Println("Deleted Message")
}

// GuildCreateHandler handles when a guild is created
func (bot *DiscordBot) GuildCreateHandler(s *discordgo.Session, g *discordgo.GuildCreate) {
	fmt.Println("New Guild")
}

// GuildDeleteHandler handles when a guild is deleted
func (bot *DiscordBot) GuildDeleteHandler(s *discordgo.Session, g *discordgo.GuildDelete) {
	fmt.Println("Left Guild")
}

// GuildMemberCreateHandler handles when a member is added to a guild
func (bot *DiscordBot) GuildMemberCreateHandler(s *discordgo.Session, m *discordgo.GuildMemberAdd) {
	fmt.Println("New Member")
}

// GuildMemberDeleteHandler handles when a member is removed from a guild
func (bot *DiscordBot) GuildMemberDeleteHandler(s *discordgo.Session, m *discordgo.GuildMemberRemove) {
	fmt.Println("Left Member")
}

// MessageReactionCreate handles when a reaction is added to a message
func (bot *DiscordBot) MessageReactionCreate(s *discordgo.Session, r *discordgo.MessageReactionAdd) {
	fmt.Println("New Reaction")
}

// MessageReactionDelete handles when a reaction is removed from a message
func (bot *DiscordBot) MessageReactionDelete(s *discordgo.Session, r *discordgo.MessageReactionRemove) {
	fmt.Println("Deleted Reaction")
}
