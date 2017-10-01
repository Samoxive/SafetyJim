package safetyjim

import (
	"../config"
	"github.com/bwmarrin/discordgo"
)

func New(config *config.Config) (*DiscordBot, error) {
	sessions := make([]*discordgo.Session, 2)

	pingCommand := NewPing()
	usages := map[string]GetUsage{"ping": pingCommand.GetUsage}
	commands := map[string]Run{"ping": pingCommand.Run}

	bot := &DiscordBot{&sessions, &usages, &commands}

	for i := 0; i < 2; i++ {
		discord, err := discordgo.New("Bot " + config.Jim.Token)
		if err != nil {
			return nil, err
		}

		discord.ShardCount = 2
		discord.ShardID = i

		discord.AddHandler(bot.ReadyHandler)
		discord.AddHandler(bot.MessageCreateHandler)
		discord.AddHandler(bot.MessageDeleteHandler)
		discord.AddHandler(bot.GuildCreateHandler)
		discord.AddHandler(bot.GuildDeleteHandler)
		discord.AddHandler(bot.GuildMemberCreateHandler)
		discord.AddHandler(bot.GuildMemberDeleteHandler)
		discord.AddHandler(bot.MessageReactionCreate)
		discord.AddHandler(bot.MessageReactionDelete)

		err = discord.Open()
		if err != nil {
			return nil, err
		}

		(*bot.Sessions)[i] = discord
	}

	return bot, nil
}

type DiscordBot struct {
	Sessions *[]*discordgo.Session
	Usages   *map[string]GetUsage
	Commands *map[string]Run
}
