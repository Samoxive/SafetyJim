package safetyjim

import (
	"github.com/bwmarrin/discordgo"
)

// The Ping command is a simple command that respons "pong" whenever a user sends "ping"
type Ping struct {
	usage []string
}

// NewPing creates a Ping type
func NewPing() Ping {
	return Ping{
		usage: []string{"ping - pong"},
	}
}

// Run handles the main bot process
func (p *Ping) Run(session *discordgo.Session, bot *DiscordBot, msg *discordgo.MessageCreate, args string) chan (bool) {
	result := make(chan (bool))

	go func() {
		session.Lock()
		defer session.Unlock()
		session.ChannelMessageSend(msg.ChannelID, "Pong "+session.LastHeartbeatAck.String())
		result <- false
	}()

	return result
}

// GetUsage returns the bot's usage
func (p *Ping) GetUsage() []string {
	return p.usage
}
