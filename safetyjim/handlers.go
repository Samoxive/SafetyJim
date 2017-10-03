package safetyjim

import (
	"fmt"

	"github.com/bwmarrin/discordgo"
)

// ReadyHandler handles when the bot is initialized.
func (bot *DiscordBot) ReadyHandler(s *discordgo.Session, r *discordgo.Ready) {
	fmt.Println("Ready")
	fmt.Println((*bot.Usages)["ping"]()[0])
}

// MessageCreateHandler handles when a message is sent
func (bot *DiscordBot) MessageCreateHandler(s *discordgo.Session, m *discordgo.MessageCreate) {
	fmt.Println("New Message")

	if m.Author.Bot {
		return
	}

	x := 0

	for i := 0; i < 2; i++ {
		y, _ := (*bot.Sessions)[i].UserGuilds(0, "", "")
		x += len(y)
	}

	println(x)
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
