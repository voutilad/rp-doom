package com.redpanda.doom.fn;

import com.redpanda.doom.model.Event;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;

public class EventRateAggregator implements AggregateFunction<Event, Tuple2<String, Integer>, Tuple2<String, Double>> {

  private final String eventType;
  private final int windowWidth;

  public EventRateAggregator(String eventType, int windowWidthMillis) {
    this.eventType = eventType;
    this.windowWidth = windowWidthMillis;
  }

  @Override
  public Tuple2<String, Integer> createAccumulator() {
    return Tuple2.of("", 0);
  }

  @Override
  public Tuple2<String, Integer> add(Event value, Tuple2<String, Integer> accumulator) {
    if (value.getType().equalsIgnoreCase(this.eventType)) {
      return Tuple2.of(
          accumulator.f0.isBlank() ? value.getSession() : accumulator.f0,
          accumulator.f1 + 1
      );
    }
    return accumulator;
  }

  @Override
  public Tuple2<String, Double> getResult(Tuple2<String, Integer> accumulator) {
    return Tuple2.of(accumulator.f0, (1000.0d * accumulator.f1) / this.windowWidth);
  }

  @Override
  public Tuple2<String, Integer> merge(Tuple2<String, Integer> a, Tuple2<String, Integer> b) {
    return Tuple2.of(
        a.f0.isBlank() ? b.f0 : a.f0,
        a.f1 + b.f1
    );
  }
}
