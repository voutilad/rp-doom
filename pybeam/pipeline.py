#!/usr/src/env python3

import json
import logging

import apache_beam as beam
from apache_beam.io.kafka import ReadFromKafka
from apache_beam.options.pipeline_options import PipelineOptions

from typing import *

def run(bootstrap_servers: str, topics: str, pipeline_options: PipelineOptions,
        use_tls: bool = False, username: str = "", password: str = "",
        sasl_mechanism: str = "PLAIN"):

    # Prepare our Consumer Configuration.
    consumer_config = {
        "bootstrap_servers": bootstrap_servers,
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
    else:
        raise RuntimeException("unexpected tls & sasl condition!")

    # Construct our Beam Pipeline
    with beam.Pipeline(options=pipeline_options) as pipeline:
        _ = (
            pipeline
            | "Read from Redpanda" > beam.io.ReadFromKafka(
                consumer_config=consumer_config,
                topics=topics,
                timestamp_policy="CreateTime")
            | "Parse JSON" > beam.Map(json.loads)
            | "Just Print" > beam.Map(logging.info)
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
    parser.add_argument(
        "--user",
    )
    parser.add_argument(
        "--password",
    )
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
    known_args, pipeline_args = parser.parse_known_args()

    pipeline_options = PipelineOptions(pipeline_args, streaming=True)

    print(f"xxx known_args = {known_args}")