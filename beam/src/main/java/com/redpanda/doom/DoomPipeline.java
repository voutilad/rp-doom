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

import com.redpanda.doom.dofn.Echo;
import com.redpanda.doom.dofn.EventDeserializer;
import com.redpanda.doom.model.Event;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.transforms.windowing.SlidingWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.StreamSupport;


public class DoomPipeline {
  private static Logger logger;
  final static int DEFAULT_WINDOW_WIDTH_MS = 1_000;
  final static int DEFAULT_SLIDE_WIDTH_MS = 25;

  public static String withNamespace(String keyName) {
    return DoomPipeline.class.getPackage().getName() + "." + keyName;
  }

  /**
   * Generate our Redpanda Consumer configuration using a provided {@link PipelineOptions}, falling back to the system
   * properties, and finally to default values.
   *
   * @param options {@link PipelineOptions}
   * @return new {@link Map} of consumer configs
   */
  public static Map<String, Object> consumerConfig(PipelineOptions options) {
    // TODO: use pipeline options :)
    final Properties props = System.getProperties();
    final Map<String, Object> config = new HashMap<>();

    // Instead of exposing the security protocol string, expose a simple boolean flag for TLS.
    final boolean useTls = props
        .getProperty(withNamespace("useTls"), "false")
        .equalsIgnoreCase("true");

    // SASL?
    final String mechanism = props.getProperty(withNamespace("saslMechanism"), "");
    if (mechanism.equalsIgnoreCase("plain")
        || mechanism.equalsIgnoreCase("scram-sha-256")
        || mechanism.equalsIgnoreCase("scram-sha-512")) {

        // Be stylish and make sure we use all caps and yell our mechanism at the machine.
        config.put("sasl.mechanism", mechanism.toUpperCase(Locale.ENGLISH));

        // Assemble our jaasConfig string...it's a beast.
        final String username = props.getProperty(withNamespace("username"), "doom");
        final String password = props.getProperty(withNamespace("password"), "doom");
        final String jaasConfig = ((mechanism.equalsIgnoreCase("plain"))
            ? "org.apache.kafka.common.security.plain.PlainLoginModule required "
            : "org.apache.kafka.common.security.scram.ScramLoginModule required ")
            + "username=\"" + username + "\" password=\"" + password + "\";";
        config.put("sasl.jaas.config", jaasConfig);

        if (useTls)
          config.put("security.protocol", "SASL_SSL");
        else
          config.put("security.protocol", "SASL_PLAINTEXT");
    } else {
      // No SASL Mechanism. Sad!
      if (useTls)
        config.put("security.protocol", "SSL");
      else
        config.put("security.protocol", "PLAINTEXT");
    }

    // Additional properties...
    config.put("auto.offset.reset", "latest");
    config.put("group.id", props.getProperty(withNamespace("groupId"), "doom"));
    config.put("group.instance.id", props.getProperty(withNamespace("groupInstanceId"), "beam-consumer"));

    return config;
  }

  public static void run(PipelineOptions options) {
    final Pipeline pipeline = Pipeline.create(options);
    final Map<String, Object> consumerConfig = consumerConfig(options);

    // Log our intended configuration, but don't print the password ;-)
    logger.info("Prepared Redpanda consumer config: ");
    consumerConfig.forEach((k, v) -> {
      var value = k.equalsIgnoreCase("sasl.jaas.config")
          ? v.toString().replaceAll("password=\"(.*?)\"", "password=\"*********\"")
          : v.toString();
      logger.info("  " + k + ": " + value);
    });

    /*
     * Group game events into sliding Windows.
     */
    PCollection<KV<String, Event>> windowedEvents = pipeline
        .apply("Connect to Redpanda",
            KafkaIO.<String, String>read()
                .withBootstrapServers(System.getProperty(withNamespace("bootstrapServers"), "localhost:9092"))
                .withTopic("doom")
                .withKeyDeserializer(StringDeserializer.class)
                .withValueDeserializer(StringDeserializer.class) // XXX: for now we'll do JSON decoding in the pipeline
                .withConsumerConfigUpdates(consumerConfig)
                .withoutMetadata())
        .apply("Extract Values",
            Values.create())
        .apply("Deserialize JSON to Events",
            ParDo.of(new EventDeserializer()))
        .apply("Filter to only Player Events",
            Filter.by(Util.actorTypeIs("player")))
        .apply("SlidingWindows",
            Window.into(SlidingWindows.of(Duration.millis(DEFAULT_WINDOW_WIDTH_MS))
                .every(Duration.millis(DEFAULT_SLIDE_WIDTH_MS))));

    // Create filtered streams based on event type, windowing as appropriate.
    PCollection<KV<String, Event>> moves = windowedEvents
        .apply("Filter Moves", Filter.by(Util.eventTypeIs("move")));
    PCollection<KV<String, Long>> movesPerUser = moves.apply(Count.perKey());


    PCollection<KV<String, Double>> killRatePerUser = windowedEvents
        .apply("Collect kills per Player", GroupByKey.create())
        .apply("Compute KPS per Player", MapElements
            .into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.doubles()))
            .via(kv -> {
              long cnt = 0;
              assert kv != null && kv.getValue() != null;
              cnt = StreamSupport.stream(kv.getValue().spliterator(), true)
                  .filter(ev -> ev.getType().equalsIgnoreCase("killed")).count();
              if (cnt == 0)
                return KV.of(kv.getKey(), 0d);
              double rateMillis = (cnt * 1.0d) / (DEFAULT_WINDOW_WIDTH_MS);
              if (Double.isInfinite(rateMillis))
                return KV.of(kv.getKey(), 0.0d);
              return KV.of(kv.getKey(), rateMillis * 1000);
            }));

    // For now, let's just print to stdout
    // movesPerUser.apply(ParDo.of(new Echo("moves: ")));
    killRatePerUser.apply("Echo KPS", ParDo.of(new Echo("kills: ")));

    // For now during dev, just run for 30s.
    pipeline.run().waitUntilFinish(Duration.standardSeconds(30));
    logger.info("Pipeline finished.");
  }

  public static void main(String[] args) {
    // TODO: command line args to pipeline options
    // Set up nicer logging output.
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "[yyyy-MM-dd'T'HH:mm:ss:SSS]");
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");

    System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka", "warn");
    System.setProperty("org.slf4j.simpleLogger.log.org.apache.beam.sdk.io.kafka.KafkaUnboundedReader", "warn");

    logger = LoggerFactory.getLogger(DoomPipeline.class);

    final PipelineOptions options = PipelineOptionsFactory.create();

    run(options);
  }
}
