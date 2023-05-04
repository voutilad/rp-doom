#!/bin/sh

MINI_DRIVER="${MINI_DRIVER:-qemu2}"

# setup for a 12G k8s deployment for now using 8 cpus
minikube start \
	--nodes 4 \
	--memory 3G \
	--cpus 2 \
	--driver "${MINI_DRIVER}"
