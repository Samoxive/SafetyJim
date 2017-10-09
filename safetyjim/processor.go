package safetyjim

import (
	"github.com/bwmarrin/discordgo"
)

type ProcessorOnMessage func(*DiscordBot, *discordgo.Session, *discordgo.MessageCreate) bool
type ProcessorOnMessageDelete func(*DiscordBot, *discordgo.Session, *discordgo.MessageDelete)
type ProcessorOnReaction func(*DiscordBot, *discordgo.Session, *discordgo.MessageReactionAdd)
type ProcessorOnReactionDelete func(*DiscordBot, *discordgo.Session, *discordgo.MessageReactionRemove)
