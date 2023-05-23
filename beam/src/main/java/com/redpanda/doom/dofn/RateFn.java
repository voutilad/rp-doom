package com.redpanda.doom.dofn;

import com.redpanda.doom.model.Event;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.values.KV;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;

public class RateFn {

  public static class RateAccum {
    long sum = 0;
    long count = 0;

    long earliest = 0;
    long latest = 0;
  }

  //@Override
  public RateAccum createAccumulator() {
    return new RateAccum();
  }

  //@Override
  public RateAccum addInput(RateAccum accum, KV<String, Event> input) {
    if (accum == null || input == null || input.getValue() == null)
      return createAccumulator();

    final Event ev = input.getValue();

    accum.sum += 1; // TODO: extract data from event
    accum.count++;
    accum.earliest = Math.min(ev.getFrame().getMillis(), accum.earliest);
    accum.latest = Math.max(ev.getFrame().getMillis(), accum.latest);

    return accum;
  }

  public RateAccum mergeAccumulators(@UnknownKeyFor @NonNull @Initialized Iterable<RateAccum> accumulators) {
    final RateAccum result = createAccumulator();
    for (RateAccum accum : accumulators) {
      result.count += accum.count;
      result.sum += accum.sum;
      result.earliest = Math.min(accum.earliest, result.earliest);
      result.latest = Math.max(accum.latest, result.latest);
    }
    return result;
  }


}
