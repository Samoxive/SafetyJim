package log

import (
	"fmt"
	"time"
)

func log(s string, t string) {
	// TODO(sam): format the time better, maybe copy javascript one?
	fmt.Printf("%s [%s] %s", time.Now().String(), t, s)
}

func Info(s string) {
	log(s, "INFO")
}

func Warn(s string) {
	log(s, "WARN")
}

func Error(s string) {
	log(s, "ERR")
}
