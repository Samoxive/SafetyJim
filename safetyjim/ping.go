package safetyjim

import (
	"github.com/bwmarrin/discordgo"
)

type Ping struct {
	usage []string
}

func NewPing() Ping {
	return Ping{
		usage: []string{"ping - pong"},
	}
}

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

func (p *Ping) GetUsage() []string {
	return p.usage
}
