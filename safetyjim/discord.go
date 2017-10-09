package safetyjim

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/bwmarrin/discordgo"
)

func (bot *DiscordBot) GetMember(s *discordgo.Session, channelID string, userID string) *discordgo.Member {
	channel, _ := s.Channel(channelID)
	member, _ := s.GuildMember(channel.GuildID, userID)

	return member
}

func (bot *DiscordBot) SuccessReact(m *discordgo.Message) {
	react(bot, m, "jimsuccess:322698554294534144")
}

func (bot *DiscordBot) FailReact(m *discordgo.Message) {
	react(bot, m, "jimfail:322698553980092417")
}

func react(bot *DiscordBot, m *discordgo.Message, emojiString string) {
	request, _ := http.NewRequest("PUT", fmt.Sprintf("https://discordapp.com/api/v6/channels/%s/messages/%s/reactions/%s/@me", m.ChannelID, m.ID, emojiString), strings.NewReader(""))
	request.Header.Add("Authorization", "Bot "+bot.Config.Jim.Token)
	request.Header.Add("User-Agent", fmt.Sprintf("DiscordBot (%s, %s)", "https://github.com/samoxive/safetyjim", "0.0.1"))
	http.DefaultClient.Do(request)
}
