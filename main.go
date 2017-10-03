package main

import (
	"bufio"
  "os"
	"io"

	"SafetyJim/config"
	"SafetyJim/safetyjim"
	"SafetyJim/log"
)

func main() {
	/*
	config, err := config.New()
	if err != nil {
		os.Exit(-1)
	}
	discord, err := safetyjim.New(config)
	if err != nil {
		os.Exit(-1)
	}

	sc := make(chan os.Signal, 1)
	signal.Notify(sc, syscall.SIGINT, syscall.SIGTERM, os.Interrupt, os.Kill)
	<-sc

	for i := 0; i < 2; i++ {
		(*discord.Sessions)[i].Close()
	}
	*/
	l := log.Logger{
		Outputs: []io.Writer{bufio.NewWriter(os.Stdout)},
	}

	l.Error("hola")
	os.Stdout.Sync()
}
