package safetyjim

import (
	"github.com/bwmarrin/discordgo"
)

type OnMessage func(*DiscordBot, *discordgo.Session, *discordgo.MessageCreate)
type OnMessageDelete func(*DiscordBot, *discordgo.Session, *discordgo.MessageDelete)
type OnReaction func(*DiscordBot, *discordgo.Session, *discordgo.MessageReactionAdd)
type OnReactionDelete func(*DiscordBot, *discordgo.Session, *discordgo.MessageReactionRemove)
