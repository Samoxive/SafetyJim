package commands

import (
	"github.com/bwmarrin/discordgo"
)

type Ping struct {}

func (p *Ping) Run(session *discordgo.Session, bot *interface{}, msg *discordgo.MessageCreate, args string) chan (bool) {
	result := make(chan(bool))

	go func() {
		session.Lock()
		defer session.Unlock()
		session.ChannelMessageSend(msg.ChannelID, "Pong " + session.LastHeartbeatAck.String())
		result<-false
	}()

	return result
}

func (p *Ping) GetUsage() []string {
	return []string{"ping - pong"}
}