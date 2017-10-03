package log

import (
	"fmt"
	"time"
)

func log(s string, t string) {
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
