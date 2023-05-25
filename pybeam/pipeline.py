#!/usr/src/env python3

import json
import logging

import apache_beam as beam
from apache_beam import window
from apache_beam.io.kafka import (
    ReadFromKafka as ReadFromRedpanda, WriteToKafka as WriteToRedpanda
)
from apache_beam.io.textio import WriteToText
from apache_beam.options.pipeline_options import PipelineOptions

from typing import *


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
                timestamp_policy="CreateTime",
                key_deserializer="org.apache.kafka.common.serialization.StringDeserializer",
                value_deserializer="org.apache.kafka.common.serialization.StringDeserializer")
            | "Parse JSON" >> beam.MapTuple(lambda k,v: (k, json.loads(v)))
            | "Filter Player Events" >> beam.Filter(lambda x: x[1]["actor"]["type"] == "player")
            | "Window" >> beam.WindowInto(window.SlidingWindows(1, 0.25))
            | "Group by Player" >> beam.GroupByKey()
            | "Convert back to JSON" >> beam.MapTuple(lambda k,v: json.dumps([k, v]))
            | "Write back to Redpanda" >> WriteToRedpanda(
                producer_config=consumer_config,
                topic="beam",
                key_serializer="org.apache.kafka.common.serialization.StringDeserializer",
                value_serializer="org.apache.kafka.common.serialization.StringDeserializer")
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
