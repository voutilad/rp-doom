#!/bin/sh

RPK="podman exec -it redpanda-0 rpk"
BROKERS=localhost:9092
API_URLS=localhost:9644
PASSWORD=password

echo ">> enabling SASL on the cluster"
$RPK cluster config set enable_sasl true --api-urls "${API_URLS}"

echo ">> setting superusers"
$RPK cluster config set superusers "['admin']" --api-urls "${API_URLS}"

echo ">> setting admin password"
$RPK acl user create admin -p "${PASSWORD}" --api-urls "${API_URLS}"

echo ">> creating doom user"
$RPK acl user create doom -p doom --api-urls "${API_URLS}"

echo ">> creating doom topic and consumer group"
$RPK topic create doom \
    -p 1 -r 1 \
    --brokers "${BROKERS}" --user admin --password "${PASSWORD}"

echo ">> adding acl for doom topic"
$RPK acl create --allow-principal doom \
    --operation all \
    --topic doom --group doom \
    --brokers "${BROKERS}" --user admin --password "${PASSWORD}"

echo ">> test doom user"
$RPK topic list --brokers "${BROKERS}" \
    --user doom --password doom
