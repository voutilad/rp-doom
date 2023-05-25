/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redpanda.doom;

import com.google.gson.Gson;
import com.redpanda.doom.model.Event;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.DiscardingSink;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * DoomPipeline
 */
public class DoomPipeline {
  final static int DEFAULT_WINDOW_WIDTH_MS = 1_000;
  final static int DEFAULT_SLIDE_WIDTH_MS = 50;


  public static Properties consumerConfig(Config config) {
    final Properties props = new Properties();

    final boolean useTls = config.getBoolean(Config.KEY_TLS);
    final String mechanism = config.getString(Config.KEY_SASL_MECHANISM);

    if (mechanism.equalsIgnoreCase("plain")
        || mechanism.equalsIgnoreCase("scram-sha-256")
        || mechanism.equalsIgnoreCase("scram-sha-512")) {
      // Be stylish and make sure we use all caps and yell our mechanism at the machine.
      props.put("sasl.mechanism", mechanism.toUpperCase(Locale.ENGLISH));

      // Assemble our jaasConfig string...it's a beast.
      final String username = config.getString(Config.KEY_USER);
      final String password = config.getString(Config.KEY_PASSWORD);
      final String jaasConfig = ((mechanism.equalsIgnoreCase("plain"))
          ? "org.apache.kafka.common.security.plain.PlainLoginModule required "
          : "org.apache.kafka.common.security.scram.ScramLoginModule required ")
          + "username=\"" + username + "\" password=\"" + password + "\";";
      props.put("sasl.jaas.config", jaasConfig);

      if (useTls)
        props.put("security.protocol", "SASL_SSL");
      else
        props.put("security.protocol", "SASL_PLAINTEXT");
    } else {
      // No SASL Mechanism. Sad!
      if (useTls)
        props.put("security.protocol", "SSL");
      else
        props.put("security.protocol", "PLAINTEXT");
    }

    props.setProperty("group.instance.id", config.getString(Config.KEY_GROUP_INSTANCE_ID));

    return props;
  }

  public static KafkaSource<String> redpandaSource(Config config) {
    return KafkaSource
        .<String>builder() // XXX you need a type annotation here or Java gets sad :'(
        .setBootstrapServers(config.getString(Config.KEY_BROKERS))
        .setTopics(config.getString(Config.KEY_TOPICS))
        .setGroupId(config.getString(Config.KEY_GROUP_ID))
        .setClientIdPrefix(config.getString(Config.KEY_CLIENT_ID_PREFIX))
        .setValueOnlyDeserializer(new SimpleStringSchema())
        .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
        .setProperties(consumerConfig(config))
        .build();
  }

  private static class GsonDeserializer extends RichMapFunction<String, Event> {

    private transient Gson gson = null;

    @Override
    public void open(Configuration parameters) throws Exception {
      super.open(parameters);
      gson = new Gson();
    }

    @Override
    public Event map(String json) {
      return gson.fromJson(json, Event.class);
    }
  }

  public static DataStream<?> buildPipeline(StreamExecutionEnvironment env, Config config) {
    WindowedStream<Event, String, TimeWindow> windowPerPlayer =
        env
            .fromSource(
                redpandaSource(config),
                WatermarkStrategy.<String>forMonotonousTimestamps().withIdleness(Duration.ofSeconds(3)),
                "Redpanda")
            // Deserialize our Strings into Events using Gson
            .map(new GsonDeserializer()).name("Deserialize Event")
            // Key and Window per Player (session)
            .keyBy(Event::getSession)
            .window(SlidingEventTimeWindows.of(
                Time.milliseconds(DEFAULT_WINDOW_WIDTH_MS),
                Time.milliseconds(DEFAULT_SLIDE_WIDTH_MS)));

    // KPS
    DataStream<Tuple2<String, Double>> kps = windowPerPlayer
        .aggregate(
            new AggregateFunction<Event, Tuple2<String, Integer>, Tuple2<String, Double>>() {
              @Override
              public Tuple2<String, Integer> createAccumulator() {
                return Tuple2.of("", 0);
              }

              @Override
              public Tuple2<String, Integer> add(Event value, Tuple2<String, Integer> accumulator) {
                return Tuple2.of(
                    accumulator.f0.isBlank() ? value.getSession() : accumulator.f0,
                    value.getType().equalsIgnoreCase("killed") ? accumulator.f1 + 1 : accumulator.f1
                );
              }

              @Override
              public Tuple2<String, Double> getResult(Tuple2<String, Integer> accumulator) {
                return Tuple2.of(accumulator.f0, (1000.0d * accumulator.f1) / DEFAULT_WINDOW_WIDTH_MS);
              }

              @Override
              public Tuple2<String, Integer> merge(Tuple2<String, Integer> a, Tuple2<String, Integer> b) {
                return Tuple2.of(
                    a.f0.isBlank() ? b.f0 : a.f0,
                    a.f1 + b.f1
                );
              }
            })
        .name("KPS");

    return kps
        .map(new MapFunction<Tuple2<String, Double>, Object>() {
          private final Logger logger = LoggerFactory.getLogger(this.getClass().getName() + "::LogIt");

          @Override
          public Object map(Tuple2<String, Double> value) {
            logger.info("KPS(player: " + value.f0 + ", kps: " + value.f1 + ")");
            return value;
          }
        })
        .name("LogIt");
  }

  public static void main(String[] args) throws Exception {
    Logger logger = LoggerFactory.getLogger(DoomPipeline.class);
    final Config config = new Config(args);

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(
        Configuration.fromMap(
            Map.of(
                "state.backend.type", "rocksdb")));
    var pipeline = buildPipeline(env, config);
    pipeline.addSink(new DiscardingSink<>()).name("Trashcan"); // For now, sink to the trash.

    logger.info("Starting pipeline.");
    env.execute();
    logger.info("Stopping.");
  }
}
