package safetyjim

import (
	"github.com/bwmarrin/discordgo"
)

func New(token string) (*DiscordBot, error) {
	sessions := make([]*discordgo.Session, 2)
	for i := 0; i < 2; i++ {
		discord, err := discordgo.New("Bot " + token)
		if err != nil {
			return nil, err
		}

		discord.ShardCount = 2
		discord.ShardID = i

		err = discord.Open()
		if err != nil {
			return nil, err
		}

		sessions[i] = discord
	}

	pingCommand := NewPing()
	usages := map[string]GetUsage{"ping": pingCommand.GetUsage}
	commands := map[string]Run{"ping": pingCommand.Run}

	bot := &DiscordBot{&sessions, &usages, &commands}

	return bot, nil
}

type DiscordBot struct {
	Sessions *[]*discordgo.Session
	Usages   *map[string]GetUsage
	Commands *map[string]Run
}
