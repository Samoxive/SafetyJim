package safetyjim

import (
	"github.com/bwmarrin/discordgo"
)

type GetUsage func() []string
type Run func(session *discordgo.Session, bot *DiscordBot, msg *discordgo.MessageCreate, args string) chan (bool)
