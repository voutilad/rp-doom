#!/usr/src/env python3

import json
import logging

from collections import namedtuple

import apache_beam as beam
from apache_beam import window, trigger
from apache_beam.io.kafka import (
    ReadFromKafka as ReadFromRedpanda, WriteToKafka as WriteToRedpanda
)
from apache_beam.options.pipeline_options import PipelineOptions

from typing import Any, Dict, Generator, Tuple


class Echo(beam.DoFn):
    def process(self, row: Any) -> Generator[Any, None, None]:
        print(f"row: {row}")
        yield row


def build_jaas_config(mechanism: str, username: str, password: str):
    base = "org.apache.kafka.common.security"
    if mechanism.startswith("SCRAM"):
        return f"{base}.scram.ScramLoginModule required " \
            + f'username="{username}" password="{password}";'
    return f"{base}.plain.PlainLoginModule required" \
        + f'username="{username}" password="{password}";'


def run(bootstrap_servers: str, topic: str, options: PipelineOptions,
        use_tls: bool = False, username: str = "", password: str = "",
        sasl_mechanism: str = "PLAIN"):

    # Prepare our Consumer Configuration.
    consumer_config = {
        "bootstrap.servers": bootstrap_servers,
        "security.protocol": "PLAINTEXT",
    }
    if username:
        consumer_config.update({
            "sasl.mechanism": sasl_mechanism,
            "sasl.jaas.config": build_jaas_config(
                sasl_mechanism, username, password
            ),
        })
    if use_tls and username:
        consumer_config.update({ "security.protocol": "SASL_SSL" })
    elif use_tls:
        consumer_config.update({ "security.protocol": "SSL" })
    elif username:
        consumer_config.update({ "security.protocol": "SASL_PLAINTEXT" })

    # Construct our Beam Pipeline
    with beam.Pipeline(options=options) as pipeline:
        per_player = (
            pipeline
            | "Read from Redpanda" >> ReadFromRedpanda(
                consumer_config=consumer_config,
                topics=[topic],
                timestamp_policy="CreateTime")
            | "Parse" >> beam.MapTuple(
                lambda k,v: (k.decode("utf8"), json.loads(v.decode("utf8")))
            ).with_output_types(Tuple[str, Dict[str, Any]])
            | "Filter Player Events" >> beam.Filter(lambda x: x[1]["actor"]["type"] == "player")
            | "Window" >> beam.WindowInto(window.SlidingWindows(1, 0.25),
                                          trigger=trigger.Repeatedly(window.AfterProcessingTime()))
            | "Group by Player" >> beam.GroupByKey()
        )

        _ = (
            per_player
            | "Serialize" >> beam.MapTuple(
                lambda k,v: (json.dumps(k).encode("utf8"), json.dumps(v).encode("utf8"))
            ).with_output_types(Tuple[bytes, bytes])
            | "Output" >> beam.io.textio.WriteToText("/tmp/beam-*.txt")
        )


if __name__ == "__main__":
    logging.getLogger().setLevel(logging.INFO)

    import argparse
    import os

    # We use a client facing interface like rpk.
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--brokers",
        dest="bootstrap_servers",
        default=os.environ.get("REDPANDA_BROKERS", "localhost:9092"),
    )
    parser.add_argument("--topic", default="doom")
    parser.add_argument("--user", dest="username")
    parser.add_argument("--password")
    parser.add_argument(
        "--sasl-mechanism",
        dest="sasl_mechanism",
        default = "PLAIN",
    )
    parser.add_argument(
        "--tls-enabled",
        dest="use_tls",
        default=False,
        action=argparse.BooleanOptionalAction,
    )
    args, pipeline_args = parser.parse_known_args()

    pipeline_options = PipelineOptions(pipeline_args, save_main_session=True, streaming=True)

    logging.info(f"Starting job with args: {args}")
    logging.info(f"Beam options: {pipeline_options}")

    run(args.bootstrap_servers, args.topic, pipeline_options, args.use_tls,
        args.username, args.password, args.sasl_mechanism)
