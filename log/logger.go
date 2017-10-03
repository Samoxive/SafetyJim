package log

import (
	"io"
	"fmt"
	"time"
)

type Logger struct {
	Outputs []io.Writer
}

func (l *Logger) log(s string, t string) {
	printString := fmt.Sprintf("%s [%s] %s", time.Now().String(), t, s)
	for _, writer := range l.Outputs {
		writer.Write([]byte(printString))
	}
}

func (l *Logger) Info(s string) {
	l.log(s, "INFO")
}

func (l *Logger) Warn(s string) {
	l.log(s, "WARN")
}

func (l *Logger) Error(s string) {
	l.log(s, "ERR")
}

