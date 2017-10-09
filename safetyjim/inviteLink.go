package safetyjim

import (
	"net/url"
	"strings"

	"github.com/bwmarrin/discordgo"
)

type InviteLink struct {
	BlacklistedHosts    []string
	ShortestsHostLength int
	WhilelistedRoles    []string
}

func NewInviteLink() InviteLink {
	return InviteLink{
		[]string{"discord.gg"}, 10, []string{"ADMINISTRATOR",
			"BAN_MEMBERS",
			"KICK_MEMBERS",
			"MANAGE_ROLES",
			"MANAGE_MESSAGES"},
	}
}

func (i *InviteLink) InviteLinkOnMessage(bot *DiscordBot, s *discordgo.Session, m *discordgo.MessageCreate) bool {
	member := bot.GetMember(s, m.ChannelID, m.Author.ID)
	for _, role := range i.WhilelistedRoles {
		for _, memberRole := range member. {
			if role == memberRole {
				s.ChannelMessageSend(m.ChannelID, "You are exempt from invite link inspection")
				return false
			}
		}
	}

	splitWords := strings.Split(m.Content, " ")

	for _, word := range splitWords {
		if len(word) <= 10 {
			continue
		}

		result, err := url.Parse(word)
		if err != nil {
			continue
		}

		for _, blacklistedHost := range i.BlacklistedHosts {
			if result.Host == blacklistedHost {
				s.ChannelMessageDelete(m.ChannelID, m.ID)
				s.ChannelMessageSend(m.ChannelID, "Bad "+m.Author.Mention()+", you can't send invite links here!")
				return true
			}
		}
	}

	return false
}
