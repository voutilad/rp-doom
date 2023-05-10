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

import com.redpanda.doom.model.Event;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.vendor.grpc.v1p48p1.com.google.gson.Gson;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.joda.time.Duration;

import java.util.HashMap;
import java.util.Map;


public class DoomPipeline {
  public static void main(String[] args) {
    final PipelineOptions options = PipelineOptionsFactory.create();
    final Pipeline p = Pipeline.create(options);

    final String jassConfig = "org.apache.kafka.common.security.scram.ScramLoginModule required " +
        "username=\"" + System.getProperty("com.redpanda.doom.Username", "admin") +
        "\" password=\"" + System.getProperty("com.redpanda.doom.Password", "admin") + "\";";

    final Map<String, Object> consumerConfig = new HashMap<>();
    consumerConfig.put("auto.offset.reset", "earliest");
    consumerConfig.put("group.id", "doom-consumer");
    consumerConfig.put("group.instance.id", "doom-consumer-beam");
    consumerConfig.put("security.protocol", "SASL_SSL");
    consumerConfig.put("sasl.mechanism", "SCRAM-SHA-256");
    consumerConfig.put("sasl.jaas.config", jassConfig);

    final Gson gson = new Gson();

    p.apply(
            KafkaIO.<String, String>read()
                .withBootstrapServers(System.getProperty("com.redpanda.doom.BootstrapServers", "localhost"))
                .withTopic("doom")
                .withKeyDeserializer(StringDeserializer.class)
                .withValueDeserializer(StringDeserializer.class)
                .withConsumerConfigUpdates(consumerConfig)
                .withoutMetadata())
        .apply("Create Values", Values.create())
        .apply("Deserialize JSON",
            MapElements
                .into(TypeDescriptor.of(Event.class))
                .via((String s) -> gson.fromJson(s, Event.class)))
        .apply("Just Print",
            MapElements
                .via(new SimpleFunction<Event, Event>() {
                  public Event apply(Event event) {
                    System.out.println("Got Event: " + event);
                    return event;
                  }
                }));

    // For now during dev, just run for 30s.
    p.run().waitUntilFinish(Duration.standardSeconds(30));
  }
}
