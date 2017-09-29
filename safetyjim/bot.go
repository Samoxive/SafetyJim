package safetyjim

import
(
	"github.com/bwmarrin/discordgo"

	"../commands"
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
	pingCommand := commands.Ping{}
	commandArray := []*commands.Command{&pingCommand}

	bot := &DiscordBot{sessions, commandArray}

	return bot, nil
}

type DiscordBot struct {
	Sessions []*discordgo.Session
	Commands []*commands.Command
}
