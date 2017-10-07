package main

import (
	"os"
	"os/signal"
	"syscall"

	"SafetyJim/config"
	"SafetyJim/log"
	"SafetyJim/safetyjim"
)

func main() {
	config, err := config.New()
	if err != nil {
		log.Error("Could not read config file! " + err.Error())
		os.Exit(1)
	}

	discord, err := safetyjim.New(config)
	if err != nil {
		log.Error("Could not initialize discord clients! " + err.Error())
		os.Exit(1)
	}

	sc := make(chan os.Signal, 1)
	signal.Notify(sc, syscall.SIGINT, syscall.SIGTERM, os.Interrupt, os.Kill)
	<-sc

	discord.Close()
}
