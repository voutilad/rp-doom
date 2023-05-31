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

import com.redpanda.doom.fn.EventRateAggregator;
import com.redpanda.doom.fn.FilterActorType;
import com.redpanda.doom.fn.GsonDeserializer;
import com.redpanda.doom.model.Event;
import com.redpanda.doom.model.Metric;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.sink2.StatefulSink;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.DiscardingSink;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Transformation logic for our pipeline(s) independent of sources and sinks. This allows us to test things more
 * easily.
 */
public class Pipeline {
  final static Logger logger = LoggerFactory.getLogger(Pipeline.class);
  final static int DEFAULT_WINDOW_WIDTH_MS = 3_000;
  final static int DEFAULT_SLIDE_WIDTH_MS = 500;
  public static final SinkFunction<Metric> DEFAULT_SINK = new DiscardingSink<>();

  final Config config;
  final StreamExecutionEnvironment env;
  final Source<String, ?, ?> source;
  final WatermarkStrategy<String> watermarkStrategy;
  final SinkFunction<Metric> sink;
  final StatefulSink<Metric, ?> statefulSink;

  private Pipeline(Config config, StreamExecutionEnvironment env, Source<String, ?, ?> source,
                   WatermarkStrategy<String> watermarkStrategy, SinkFunction<Metric> sink,
                   StatefulSink<Metric, ?> statefulSink) {
    this.config = config;
    this.env = env;
    this.source = source;
    this.sink = sink;
    this.statefulSink = statefulSink;
    this.watermarkStrategy = watermarkStrategy;
  }

  public static PipelineBuilder builder() {
    return new PipelineBuilder();
  }

  public static class PipelineBuilder {
    private Config config = new Config(new String[]{});
    private StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    private SinkFunction<Metric> sink = DEFAULT_SINK;
    private StatefulSink<Metric, ?> statefulSink = null;
    private Source<String, ?, ?> source = null;
    private WatermarkStrategy<String> watermarkStrategy = WatermarkStrategy.forMonotonousTimestamps();

    private PipelineBuilder() { }

    public PipelineBuilder withConfig(Config config) {
      this.config = config;
      return this;
    }

    public PipelineBuilder withEnvironment(StreamExecutionEnvironment env) {
      this.env = env;
      return this;
    }

    public PipelineBuilder fromSource(Source<String, ?, ?> source) {
      this.source = source;
      return this;
    }

    public PipelineBuilder toSink(SinkFunction<Metric> sink) {
      this.sink = sink;
      return this;
    }

    public PipelineBuilder toStatefulSink(StatefulSink<Metric, ?> sink) {
      this.statefulSink = sink;
      return this;
    }

    public PipelineBuilder usingWatermarkStrategy(WatermarkStrategy<String> watermarkStrategy) {
      this.watermarkStrategy = watermarkStrategy;
      return this;
    }

    public Pipeline build() {
      return new Pipeline(config, env, source, watermarkStrategy, sink, statefulSink);
    }
  }

  private JobClient execute(String eventType, String actorType) throws Exception {
    // Establish our stream of Events, filtered to just the actor type we want.
    DataStream<Event> eventStream =
        env
            .fromSource(source, watermarkStrategy, "Source")
            .map(new GsonDeserializer<>(Event.class))
            .returns(Types.POJO(Event.class))
            .name("Deserialize JSON")
            .filter(FilterActorType.of(actorType))
            .returns(Types.POJO(Event.class))
            .name("Filter actorType=" + actorType);

    // First we need to window our input stream.
    WindowedStream<Event, String, TimeWindow> windowPerPlayer =
        eventStream
            // Key and Window per Player (session)
            .keyBy(Event::getSession)
            .window(SlidingEventTimeWindows.of(
                Time.milliseconds(DEFAULT_WINDOW_WIDTH_MS),
                Time.milliseconds(DEFAULT_SLIDE_WIDTH_MS)));

    // Generate our metrics.
    var metrics =
        windowPerPlayer
            .aggregate(new EventRateAggregator(eventType, DEFAULT_WINDOW_WIDTH_MS))
            .name("Compute Raw Events per Second")
            .map(tuple -> new Metric(tuple.f0, eventType + " per second", tuple.f1))
            .returns(Types.POJO(Metric.class))
            .name("Convert to Metric");


    // If we have a stateless sink, like a logger, just wire it in.
    if (sink != null) {
      metrics
          .map(value -> {
            System.out.println(value);
            return value;
          })
          .returns(Types.POJO(Metric.class))
          .name("Logger")
          .addSink(sink);
    }

    // Do we have a stateful sink like Redpanda?
    if (statefulSink != null) {
      metrics
          .keyBy(Metric::getSession)
          .sinkTo(statefulSink)
          .name("Stateful Sink");
    }

    // Make it so.
    logger.info("Executing plan:\n" + env.getExecutionPlan());
    return env.executeAsync();
  }

  public JobExecutionResult run(String eventType, String actorType) {
    try {
      final JobClient client = execute(eventType, actorType);
      logger.info("Started synchronous job: " + client.getJobID());
      return client.getJobExecutionResult().get();
    } catch (ExecutionException e) {
      logger.error("execution failed");
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      logger.error("interrupted");
      throw new RuntimeException(e);
    } catch (Exception e) {
      logger.error("unknown exception");
      throw new RuntimeException(e);
    }
  }

  public CompletableFuture<JobExecutionResult> runAsync(String eventType, String actorType) {
    try {
      final JobClient client = execute(eventType, actorType);
      logger.info("Started asynchronous job: " + client.getJobID());
      return client.getJobExecutionResult();
    } catch (ExecutionException e) {
      logger.error("execution failed");
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      logger.error("interrupted");
      throw new RuntimeException(e);
    } catch (Exception e) {
      logger.error("unknown exception");
      throw new RuntimeException(e);
    }
  }

}
