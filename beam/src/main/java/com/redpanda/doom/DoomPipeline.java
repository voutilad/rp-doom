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
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.CoderException;
import org.apache.beam.sdk.coders.CoderRegistry;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.vendor.grpc.v1p48p1.com.google.gson.Gson;
import org.apache.beam.vendor.grpc.v1p48p1.com.google.gson.GsonBuilder;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.joda.time.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
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
    consumerConfig.put("group.id",
        System.getProperty("com.redpanda.doom.GroupId", "doom-consumers"));
    consumerConfig.put("group.instance.id",
        System.getProperty("com.redpanda.doom.GroupInstanceId", "doom-beam-consumer"));
    consumerConfig.put("security.protocol", "SASL_SSL");
    consumerConfig.put("sasl.mechanism", "SCRAM-SHA-256");
    consumerConfig.put("sasl.jaas.config", jassConfig);

    p.apply(
            KafkaIO.<String, String>read()
                .withBootstrapServers(System.getProperty("com.redpanda.doom.BootstrapServers", "localhost"))
                .withTopic("doom")
                .withKeyDeserializer(StringDeserializer.class)
                .withValueDeserializer(StringDeserializer.class)
                .withConsumerConfigUpdates(consumerConfig)
                .withoutMetadata())
        .apply("Create Values", Values.create())
        .apply("Deserialize JSON", ParDo.of(new DoFn<String, Event>() {
          private Gson gson;
          @Setup
          public void setUp() {
            gson = new Gson();
          }

          @ProcessElement
          public void processElement(ProcessContext context) {
            final String json = context.element();
            context.output(gson.fromJson(json, Event.class));
          }
        }))
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
