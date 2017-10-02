package safetyjim

import (
	"github.com/bwmarrin/discordgo"
)

// GetUsage returns the usage for a bot
type GetUsage func() []string

// Run runs a bot on a given session with the provided creation message and arguments
// Returns boolean indicating success status
type Run func(session *discordgo.Session, bot *DiscordBot, msg *discordgo.MessageCreate, args string) chan (bool)
