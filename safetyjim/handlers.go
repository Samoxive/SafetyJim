package safetyjim

import (
	"fmt"

	"github.com/bwmarrin/discordgo"
)

func (bot *DiscordBot) ReadyHandler(s *discordgo.Session, r *discordgo.Ready) {
	fmt.Println("Ready")
	fmt.Println((*bot.Usages)["ping"]()[0])
}

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

func (bot *DiscordBot) MessageDeleteHandler(s *discordgo.Session, m *discordgo.MessageDelete) {
	fmt.Println("Deleted Message")
}

func (bot *DiscordBot) GuildCreateHandler(s *discordgo.Session, g *discordgo.GuildCreate) {
	fmt.Println("New Guild")
}

func (bot *DiscordBot) GuildDeleteHandler(s *discordgo.Session, g *discordgo.GuildDelete) {
	fmt.Println("Left Guild")
}

func (bot *DiscordBot) GuildMemberCreateHandler(s *discordgo.Session, m *discordgo.GuildMemberAdd) {
	fmt.Println("New Member")
}

func (bot *DiscordBot) GuildMemberDeleteHandler(s *discordgo.Session, m *discordgo.GuildMemberRemove) {
	fmt.Println("Left Member")
}

func (bot *DiscordBot) MessageReactionCreate(s *discordgo.Session, r *discordgo.MessageReactionAdd) {
	fmt.Println("New Reaction")
}

func (bot *DiscordBot) MessageReactionDelete(s *discordgo.Session, r *discordgo.MessageReactionRemove) {
	fmt.Println("Deleted Reaction")
}
