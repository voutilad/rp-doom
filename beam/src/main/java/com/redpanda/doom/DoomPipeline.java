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

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Values;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.HashMap;
import java.util.Map;

public class DoomPipeline {
  static final String TOKENIZER_PATTERN = "[^\\p{L}]+"; // Java pattern for letters

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
    consumerConfig.put("sasl.mechanism", "SCAM-SHA-256");
    consumerConfig.put("sasl.jaas.config", jassConfig);

    p.apply(
            KafkaIO.<String, String>read()
                .withBootstrapServers(System.getProperty("com.redpanda.doom.BootstrapServers", "localhost"))
                .withTopic("doom")
                .withKeyDeserializer(StringDeserializer.class)
                .withValueDeserializer(StringDeserializer.class)
                .withConsumerConfigUpdates(consumerConfig)
                .withoutMetadata())
        .apply(Values.create())
        .apply(
            "Just Print",
            ParDo.of(
                new DoFn<String, String>() {
                  @ProcessElement
                  public void processElement(ProcessContext c) {
                    System.out.println(c.element());
                  }
                }));
    p.run().waitUntilFinish();
  }
}
