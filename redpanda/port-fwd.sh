#!/bin/sh

# abort on error, turn on job control
set -em

echo ">> Setting up local port fowarding to k8s..."
kubectl -n redpanda port-forward svc/redpanda-console 8080:8080 2>&1 > /dev/null &

echo ">> Popping your browser..."
case $(uname) in
    "Darwin")
        open "http://localhost:8080"
        ;;
    *)
        echo "Sorry, but you have to manually go to http://localhost:8080"
        ;;
esac

echo ">> Hit ctrl-c when finished :)"
fg %1
