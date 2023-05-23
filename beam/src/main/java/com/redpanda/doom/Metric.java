package com.redpanda.doom;

import com.redpanda.doom.model.Event;

public class Metric<T> {
  public long earliestMillis = 0;
  public long latestMillis = 0;

  public T value = null;

  public static Metric fromEvent(Event event) {
    Metric metric = new Metric();
    metric.earliestMillis = event.getFrame().getMillis();
    metric.latestMillis = metric.earliestMillis;
    metric.value = 0;

    return metric;
  }
}
