#!/usr/src/env python3

import json
import logging

from collections import namedtuple

import apache_beam as beam
from apache_beam import window
from apache_beam.io.kafka import (
    ReadFromKafka as ReadFromRedpanda, WriteToKafka as WriteToRedpanda
)
from apache_beam.io.textio import WriteToText
from apache_beam.options.pipeline_options import PipelineOptions

from typing import Any, Dict, Generator, NamedTuple, List, Tuple, TypeVar, Union


Event = Dict[str, Any]
RawKV = Tuple[Union[None, bytes], Union[None, bytes]]
K = TypeVar("K")
V = TypeVar("V")
KV = Tuple[K, V]


class Unpackage(beam.DoFn):
    def process(self, data: RawKV) -> Generator[KV, Any, Any]:
        try:
            _data = tuple(data)
            key = (data[0] or bytes()).decode("utf8")
            value = json.loads((data[1] or bytes()).decode("utf8"))
            yield key, value
        except Exception as e:
            print(f"Oh crap! {e}")


class PackageUp(beam.DoFn):
    def process(self, data: Tuple[str, List[Event]]) -> Generator[RawKV, None, None]:
        try:
            key, value = data
            yield (key.encode("utf8"), json.dumps(value).encode("utf8"))
        except Exception as e:
            print(f"Oh crap: {e}")


def run(bootstrap_servers: str, topics: str, pipeline_options: PipelineOptions,
        use_tls: bool = False, username: str = "", password: str = "",
        sasl_mechanism: str = "PLAIN"):

    # Prepare our Consumer Configuration.
    consumer_config = {
        "bootstrap.servers": bootstrap_servers,
        "security.protocol": "PLAINTEXT",
    }
    if username:
        consumer_config.update({
            "sasl.username": username,
            "sasl.password": password,
            "sasl.mechanism": sasl_mechanism,
        })
    if use_tls and username:
        consumer_config.update({ "security.protocol": "SASL_SSL" })
    elif use_tls:
        consumer_config.update({ "security.protocol": "SSL" })
    elif username:
        consumer_config.update({ "security.protocol": "SASL_PLAINTEXT" })

    # Construct our Beam Pipeline
    with beam.Pipeline(options=pipeline_options) as pipeline:
        _ = (
            pipeline
            | "Read from Redpanda" >> ReadFromRedpanda(
                consumer_config=consumer_config,
                topics=topics,
                timestamp_policy="CreateTime")
            | "Parse JSON" >> beam.ParDo(Unpackage())
            | "Filter Player Events" >> beam.Filter(lambda x: x[1]["actor"]["type"] == "player")
            | "Window" >> beam.WindowInto(window.SlidingWindows(1, 0.25))
            | "Group by Player" >> beam.GroupByKey()
            | "Convert back to JSON" >> beam.ParDo(PackageUp())
            | "Write back to Redpanda" >> WriteToRedpanda(
                producer_config=consumer_config,
                topic="beam")
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
    parser.add_argument("--topics", default="doom")
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

    pipeline_options = PipelineOptions(pipeline_args, streaming=True)

    logging.info(f"Starting job with args: {args}")
    logging.info(f"Beam options: {pipeline_options}")

    run(args.bootstrap_servers, args.topics, pipeline_options, args.use_tls,
        args.username, args.password, args.sasl_mechanism)
