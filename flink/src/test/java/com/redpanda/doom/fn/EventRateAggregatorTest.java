package com.redpanda.doom.fn;

import com.redpanda.doom.model.Actor;
import com.redpanda.doom.model.Event;
import com.redpanda.doom.model.Frame;
import org.apache.flink.api.java.tuple.Tuple2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventRateAggregatorTest {

  private static final String EVENT_TYPE = "type";
  private static final String SESSION = "session";
  @Test
  public void testBasicAggregationMath() {
    final EventRateAggregator fn = new EventRateAggregator(EVENT_TYPE, 1000);
    Tuple2<String, Integer> tuple = fn.createAccumulator();

    tuple = fn.add(new Event(SESSION, 1, EVENT_TYPE, Frame.EMPTY_FRAME, Actor.EMPTY_ACTOR, Actor.EMPTY_ACTOR), tuple);
    Assertions.assertEquals(SESSION, tuple.f0, "should have session as key");
    Assertions.assertEquals(1, tuple.f1, "should have a count of 1");

    tuple = fn.add(new Event(SESSION, 1, EVENT_TYPE, Frame.EMPTY_FRAME, Actor.EMPTY_ACTOR, Actor.EMPTY_ACTOR), tuple);
    Assertions.assertTrue(tuple.f0.contentEquals(SESSION), "should have session as key");
    Assertions.assertEquals(2, tuple.f1, "should have a count of 2");

    Tuple2<String, Double> result = fn.getResult(tuple);
    Assertions.assertEquals(SESSION, result.f0, "should have session as key");
    Assertions.assertEquals(2.0d, result.f1, "should have a rate of 2");
  }

  @Test
  public void testEventTypeFiltering() {
    final EventRateAggregator fn = new EventRateAggregator(EVENT_TYPE, 1000);
    Tuple2<String, Integer> tuple = fn.createAccumulator();

    tuple = fn.add(new Event(SESSION, 1, EVENT_TYPE, Frame.EMPTY_FRAME, Actor.EMPTY_ACTOR, Actor.EMPTY_ACTOR), tuple);
    Assertions.assertEquals(SESSION, tuple.f0, "should have session as key");
    Assertions.assertEquals(1, tuple.f1, "should have a count of 1");

    tuple = fn.add(new Event(SESSION, 1, "CRAP", Frame.EMPTY_FRAME, Actor.EMPTY_ACTOR, Actor.EMPTY_ACTOR), tuple);
    Assertions.assertTrue(tuple.f0.contentEquals(SESSION), "should have session as key");
    Assertions.assertEquals(1, tuple.f1, "should still have a count of 1");

  }
}
