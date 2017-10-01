package config

import (
	"github.com/BurntSushi/toml"
)

type Config struct {
	Jim      Jim
	Database Database
	Metrics  Metrics
	BotList  BotList
}

type Jim struct {
	Token          string
	Default_Prefix string
	Shard_Count    int
}

type Database struct {
	User string
	Pass string
	Host string
	Name string
	Port int
}

type Metrics struct {
	Enabled        bool
	Api_Key        string
	Host           string
	Flush_Interval int
}

type BotList struct {
	Enabled bool
	List    []List
}

type List struct {
	Name          string
	Url           string
	Token         string
	Ignore_Errors bool
}

func New() (*Config, error) {
	var config Config
	_, err := toml.DecodeFile("config.toml", &config)
	if err != nil {
		return nil, err
	}

	return &config, nil
}
