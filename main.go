package main

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"./safetyjim"
)

func main() {
	discord := safetyjim.New("something")
	fmt.Println(discord)
	fmt.Println(discord.Session)
	sc := make(chan os.Signal, 1)
	signal.Notify(sc, syscall.SIGINT, syscall.SIGTERM, os.Interrupt, os.Kill)
	<-sc
}
