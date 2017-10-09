package safetyjim

import (
	"SafetyJim/config"

	"github.com/bwmarrin/discordgo"
)

// New creates a Bot from a bot Config
func New(config *config.Config) (*DiscordBot, error) {
	sessions := make([]*discordgo.Session, config.Jim.Shard_Count)

	pingCommand := NewPing()
	usages := map[string]GetUsage{"ping": pingCommand.GetUsage}
	commands := map[string]Run{"ping": pingCommand.Run}

	inviteLinkProcessor := NewInviteLink()
	onMessageProcessors := []ProcessorOnMessage{inviteLinkProcessor.InviteLinkOnMessage}
	onMessageDeleteProcessors := []ProcessorOnMessageDelete{}
	onReactionProcessors := []ProcessorOnReaction{}
	onReactionDeleteProcessors := []ProcessorOnReactionDelete{}

	processors := Processors{
		onMessageProcessors, onMessageDeleteProcessors, onReactionProcessors, onReactionDeleteProcessors,
	}

	bot := &DiscordBot{&sessions, &usages, &commands, &processors, config}

	for i := 0; i < config.Jim.Shard_Count; i++ {
		discord, err := discordgo.New("Bot " + config.Jim.Token)
		if err != nil {
			return nil, err
		}

		discord.ShardCount = config.Jim.Shard_Count
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

// Close all of the websocket clients connected to discord API
func (d *DiscordBot) Close() {
	for i := 0; i < d.Config.Jim.Shard_Count; i++ {
		(*d.Sessions)[i].Close()
	}
}

// A DiscordBot is a bot which has a set of sessions and commands.
type DiscordBot struct {
	Sessions   *[]*discordgo.Session
	Usages     *map[string]GetUsage
	Commands   *map[string]Run
	Processors *Processors
	Config     *config.Config
}

type Processors struct {
	OnMessage        []ProcessorOnMessage
	OnMessageDelete  []ProcessorOnMessageDelete
	OnReaction       []ProcessorOnReaction
	OnReactionDelete []ProcessorOnReactionDelete
}
