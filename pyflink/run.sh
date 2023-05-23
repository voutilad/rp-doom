#!/bin/sh
podman run --rm -it --network redpanda-doom_redpanda_network -v "$(pwd):/work" localhost/pyflink stream.py
