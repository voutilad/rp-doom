#!/bin/sh
echo "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
cp /tmp/redpanda.yaml /etc/redpanda/
sed -i "s/\%\%HOSTNAME\%\%/$(hostname -s)/g" /etc/redpanda/redpanda.yaml

echo "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"

/entrypoint.sh $@
