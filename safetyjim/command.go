package safetyjim

import (
	"github.com/bwmarrin/discordgo"
)

type Command interface {
	GetUsage() []string
	Run(session *discordgo.Session, bot Command, msg *discordgo.MessageCreate, args string) chan(bool)
}