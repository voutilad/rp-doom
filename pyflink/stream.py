#!/usr/bin/env python3

import configparser
import logging
import json

from pyflink.common import (
    Duration, Encoder, SimpleStringSchema, Time, Types, WatermarkStrategy
)
from pyflink.common.watermark_strategy import TimestampAssigner, WatermarkStrategy
from pyflink.datastream import (
    ProcessWindowFunction, StreamExecutionEnvironment, RuntimeExecutionMode
)
from pyflink.datastream.connectors.kafka import (
    KafkaSource, KafkaOffsetsInitializer
)
from pyflink.datastream.formats.json import JsonRowDeserializationSchema
from pyflink.datastream.window import (
    SlidingEventTimeWindows, SlidingProcessingTimeWindows, TimeWindow,
    TumblingEventTimeWindows
)

from typing import Any, Dict, Iterable, Tuple


def build_jaas_config(mechanism: str, username: str, password: str):
    if mechanism.startswith("SCRAM"):
        return f'org.apache.flink.kafka.shaded.org.apache.kafka.common.security.scram.ScramLoginModule required username="{username}" password="{password}";'
    else:
        raise RuntimeException("FINISH SUPPORTING NON-SCRAM!")


class DoomEventTimestampAssigner(TimestampAssigner):
    def extract_timestamp(self, value: Dict[str, Any], record_timestamp) -> int:
        millis = value.get("frame", {}).get("millis", 0)
        # print(f"millis={millis}, record_timestamp={record_timestamp}")
        return int(millis)


class LoggingWindowFunction(ProcessWindowFunction):
    """"""
    def process(self, key: str, context: ProcessWindowFunction.Context[TimeWindow],
                elements: Iterable[Dict[str, Any]]) -> Iterable[str]:
        msg = "(empty?)"
        if len(elements) > 0:
            msg = f"(key: {key}, sz: {len(elements)}, " + \
                f"start: {context.window().start}, end: {context.window().end})"
        # print(msg)
        yield msg


class ActionRateFunction(ProcessWindowFunction):
    def __init__(self, action_type: str):
        self.action_type = action_type

    def process(self, key: str, context: ProcessWindowFunction.Context[TimeWindow],
                elements: Iterable[Dict[str, Any]]) -> Iterable[Tuple[str, float, str]]:
        window = context.window()
        n = len(elements)
        delta = (window.end - window.start) / 1000.0
        val = float(n) / delta
        yield (key, val, window.max_timestamp(), self.action_type)


def echo(x: Any) -> Any:
    """Debugging echo function for use in a DataStream."""
    print(f">>> {x}")
    return x


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

    # Wire up our connection to Redpanda as our source.
    redpanda = (
        KafkaSource
        .builder()
        .set_bootstrap_servers(config.get("bootstrap_servers", "localhost:9092"))
        .set_topics(config.get("topics", "doom"))
        .set_group_id(config.get("group_id", "doom"))
        .set_value_only_deserializer(SimpleStringSchema())
        .set_starting_offsets(KafkaOffsetsInitializer.latest())
        .set_properties({
            "security.protocol": config.get("security_protocol", "SASL_SSL"),
            "sasl.mechanism": config.get("sasl_mechanism", "PLAIN"),
            "sasl.jaas.config": jaas,
            "client.id.prefix": "flinkyboi",
        })
        .build()
    )

    frame_watermarks = (
        WatermarkStrategy
        .for_monotonous_timestamps()
        .with_timestamp_assigner(DoomEventTimestampAssigner())
    )

    #
    # Our pipeline: aggregate moves in sliding windows, recording average speed.
    #
    ds = (
        env
        .from_source(
            source=redpanda,
            watermark_strategy=WatermarkStrategy.for_monotonous_timestamps(),
            source_name="Redpanda"
        )
        .map(lambda row: json.loads(row))
    )

    hits = (
        ds
        .filter(lambda row: row.get("type", "") == "hit")
        .key_by(lambda row: row.get("session", ""), key_type=Types.STRING())
        .window(SlidingProcessingTimeWindows.of(Time.milliseconds(500),
                                                Time.milliseconds(25)))
        .process(ActionRateFunction("hit"),
                 Types.TUPLE([Types.STRING(), Types.FLOAT(),
                              Types.LONG(), Types.STRING()]))
    )

    moves = (
        ds
        .filter(lambda row: row.get("type", "") == "move")
        #.map(echo)
        .assign_timestamps_and_watermarks(frame_watermarks)
        .key_by(lambda row: row.get("session", ""), key_type=Types.STRING())
        .window(SlidingProcessingTimeWindows.of(Time.milliseconds(200), Time.milliseconds(20)))
        .process(ActionRateFunction("move"),
                 Types.TUPLE([Types.STRING(), Types.FLOAT(),
                              Types.LONG(), Types.STRING()]))
    )

    # Sync to stdout for now.
    hits.print()
    moves.print()

    # Consume the stream.
    env.enable_checkpointing(1000)
    env.execute()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)

    config = configparser.ConfigParser()
    config.read("config.ini")
    source = dict(config["source"])
    sink = dict(config["sink"])

    print("Starting job.")
    job(source)
