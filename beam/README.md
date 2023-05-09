# Apache Beam pipeline for Gaming Achievements

TBA...but for now:

- Use Java 8 (I think that's a Beam requirement)
- Grab a copy of Maven.

## Building
```
$ mvn package
```

## Running with DirectRunner
To run locally:

```
$ java \
    -Dcom.redpanda.doom.Username=<your-sasl-username> \
    -Dcom.redpanda.doom.Password=<your-sasl-password>
    -Dcom.redpanda.doom.BootstrapServers=<your-bootstrap-servers> \
    -jar target/doom-bundled-0.1.jar
```

Currently expects:

- Topic named `doom`
- Consumer Group named `doom-consumer`

> Will be replaced with runtime config options later.
