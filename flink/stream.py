#!/usr/bin/env python3

import configparser
import logging
import json

from pyflink.common import (
    Encoder, SimpleStringSchema, Time, Types, WatermarkStrategy
)
from pyflink.datastream import (
    ProcessWindowFunction, StreamExecutionEnvironment, RuntimeExecutionMode
)
from pyflink.datastream.connectors.kafka import FlinkKafkaConsumer
from pyflink.datastream.formats.json import JsonRowDeserializationSchema
from pyflink.datastream.window import SlidingEventTimeWindows

from typing import Any, Dict, Iterable, Tuple


def load_json_schema(path: str) -> JsonRowDeserializationSchema:
    """
    Load a JSON schema file from a given path and build a Flink Deserializer.
    """
    # XXX this just plain sucks and doesn't work?
    with open(path, mode="r") as f:
        json = f.read()
        return (
            JsonRowDeserializationSchema
            .builder()
            .json_schema(json)
            .build()
        )

def build_jaas_config(mechanism: str, username: str, password: str):
    if mechanism.startswith("SCRAM"):
        return f'org.apache.flink.kafka.shaded.org.apache.kafka.common.security.scram.ScramLoginModule required username="{username}" password="{password}";'
    else:
        raise RuntimeException("FINISH SUPPORTING NON-SCRAM!")


class LoggingWindowFunction(ProcessWindowFunction):
    """"""
    def process(self, key: str, context: ProcessWindowFunction.Context,
                elements: Iterable[Tuple[str, int]]) -> Iterable[str]:
        msg = "(empty?)"
        if len(elements) > 0:
            msg = f"(key: {key}, sz: {len(elements)}, head: {elements[0]})"
        print(msg)
        yield msg


def job(config: Dict[str, Any]):
    """
    A Flink Job...tbd
    """
    env = StreamExecutionEnvironment.get_execution_environment()
    env.add_jars(config.get("kafka_jar",
                            "file:///./flink-sql-connector-kafka.jar"))
    jaas = build_jaas_config(
        config.get("sasl_mechanism", "PLAIN"),
        config.get("username", "doom"),
        config.get("password", "doom"),
    )

    # Wire up our connection to Redpanda.
    redpanda = FlinkKafkaConsumer(
        topics=config.get("topics", "doom"),
        deserialization_schema=SimpleStringSchema(),
        properties={
            "bootstrap.servers": config.get("bootstrap_servers",
                                            "localhost:9092"),
            "group.id": config.get("group_id", "doom-consumer"),
            "security.protocol": config.get("security_protocol", "SASL_SSL"),
            "sasl.mechanism": config.get("sasl_mechanism", "PLAIN"),
            "sasl.jaas.config": jaas,
        }
    )

    # Our pipeline: aggregate moves in sliding windows, recording average speed.
    ds = (
        env
        .add_source(redpanda)
        .map(lambda row: json.loads(row))
        .filter(lambda row: row.get("type", "") == "move")
        .key_by(lambda row: row["session"], key_type=Types.STRING())
        .window(SlidingEventTimeWindows.of(Time.milliseconds(5000),
                                           Time.milliseconds(500)))
        .process(LoggingWindowFunction())
    )

    # Consume the stream.
    with ds.execute_and_collect() as results:
        for result in results:
            print(result)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)

    config = configparser.ConfigParser()
    config.read("config.ini")
    source = dict(config["source"])
    sink = dict(config["sink"])

    print("Starting job.")
    job(source)
