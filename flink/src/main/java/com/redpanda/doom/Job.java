package com.redpanda.doom;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.DiscardingSink;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

public class Job {

  public static KafkaSource<String> redpandaSource(Config config) {
    return KafkaSource
        .<String>builder() // XXX you need a type annotation here or Java gets sad :'(
        .setBootstrapServers(config.getString(Config.KEY_BROKERS))
        .setTopics(config.getString(Config.KEY_TOPICS))
        .setGroupId(config.getString(Config.KEY_GROUP_ID))
        .setClientIdPrefix(config.getString(Config.KEY_CLIENT_ID_PREFIX))
        .setValueOnlyDeserializer(new SimpleStringSchema())
        .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
        .setProperties(config.toConsumerConfig())
        .build();
  }

  public static KafkaSink<String> redpandaSink(Config config) {
    return KafkaSink
        .<String>builder() // XXX you need a type annotation here or Java gets sad :'(
        .setBootstrapServers(config.getString(Config.KEY_BROKERS))
        .setRecordSerializer(KafkaRecordSerializationSchema.builder()
            .setTopic(config.getString(Config.KEY_SINK_TOPIC))
            .setValueSerializationSchema(new SimpleStringSchema())
            .build())
        .setKafkaProducerConfig(config.toProducerConfig())
        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
        .build();
  }

  public static void main(String[] args) throws Exception {
    Logger logger = LoggerFactory.getLogger(Pipeline.class);

    final Config config = new Config(args);

    // Create our execution environment. This is context aware and supports both local (direct invocation like in an
    // IDE) or submitted to a Flink cluster with remote Task nodes.
    final StreamExecutionEnvironment env = StreamExecutionEnvironment
        .getExecutionEnvironment(Configuration.fromMap(Map.of("state.backend.type", "rocksdb")))
        .setBufferTimeout(5); // 5ms flush timer to lower latency

    Pipeline.PipelineBuilder builder = Pipeline.builder()
        .withConfig(config)
        .withEnvironment(env)
        .fromSource(redpandaSource(config))
        .usingWatermarkStrategy(WatermarkStrategy.<String>forMonotonousTimestamps().withIdleness(Duration.ofSeconds(3)))
        .toSink(new DiscardingSink<>());

    if (!config.getString(Config.KEY_SINK_TOPIC).isBlank()) {
      builder = builder.toStatefulSink(redpandaSink(config));
    }

    final Pipeline pipeline = builder.build();

    // Make rocket go now!
    pipeline.run("killed", "player");
  }
}
