package commands

import (
	"github.com/bwmarrin/discordgo"

	"../safetyjim"
)

type Command interface {
	GetUsage() []string
	Run(session *discordgo.Session, bot *safetyjim.DiscordBot, msg *discordgo.MessageCreate, args string) chan(bool)
}