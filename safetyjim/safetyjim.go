package safetyjim

import (
	"fmt"
	"os"

	"github.com/bwmarrin/discordgo"
)

func New(token string) SafetyJim {
	discord, err := discordgo.New("Bot " + token)
	if err != nil {
		fmt.Println(err)
		os.Exit(1)
	}

	err = discord.Open()
	fmt.Println(err)
	if err != nil {
		fmt.Println("You f'd up.")
		fmt.Println(err)
		os.Exit(1)
	}

	return SafetyJim{discord}
}

type SafetyJim struct {
	Session *discordgo.Session
}
