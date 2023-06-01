package main

import (
	"fmt"
	"io"
	"strings"

	"github.com/buger/jsonparser"
	"github.com/rockwotj/redpanda/src/go/sdk"
)

type Message struct {
	Session string
	Metric  string
	Value   float64
}

type TooBigError struct{}

func (e *TooBigError) Error() string {
	return "Message was too big!"
}

func main() {
	redpanda.OnTransform(onTransform)
}

// Temporary buffer so that GC isn't invoked so much!
var buf = make([]byte, 4096)

func onTransform(e redpanda.TransformEvent) error {
	output, err := redpanda.CreateOutputRecord()
	if err != nil {
		return err
	}

	// copy over the key
	_, err = io.CopyBuffer(output.Key(), e.Record().Key(), buf)
	if err != nil {
		return err
	}

	// unpack the message
	sz := 0
	sz, err = e.Record().Value().Read(buf)
	if err != nil {
		fmt.Println("Oh crap, failed to read the message value!")
		return err
	}
	if sz == 0 {
		fmt.Println("Empty message? Bailing.")
		return nil
	}

	msg := Message{}
	msg.Session, err = jsonparser.GetString(buf[:sz], "session")
	if err != nil {
		fmt.Println("Oh Crap, failed to extract session field")
		return nil
	}
	msg.Metric, err = jsonparser.GetString(buf[:sz], "metric")
	if err != nil {
		fmt.Println("Oh Crap, failed to extract metric field")
		return nil
	}
	msg.Value, err = jsonparser.GetFloat(buf[:sz], "value")
	if err != nil {
		fmt.Println("Oh Crap, failed to extract value field")
		return nil
	}

	// Create our new message
	str := fmt.Sprintf("Your %s is %0.3f.", msg.Metric, msg.Value)
	if len(str)+1 > len(buf) {
		// xxx I'm supposing we need to check for an extra byte for NUL
		return &TooBigError{}
	}

	fmt.Printf("Created new message: %s\n", str)

	// copy over the value
	_, err = io.CopyBuffer(output.Value(), strings.NewReader(str), buf)
	if err != nil {
		return err
	}

	// copy over the headers
	for _, k := range e.Record().Headers().Keys() {
		v := e.Record().Headers().Get(k)
		err = output.AppendHeader(k, v)
		if err != nil {
			return err
		}
	}

	return nil
}
