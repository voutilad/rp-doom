package com.redpanda.doom;

import com.redpanda.doom.model.Event;
import org.apache.beam.sdk.transforms.ProcessFunction;
import org.apache.beam.sdk.values.KV;

/**
 * Centralized logic to generate predicates and functions for use in a Beam pipeline processing {@link Event}s.
 */
public class Util {

  private interface PredicateKV extends ProcessFunction<KV<String, Event>, Boolean> {}

  /**
   * Generate a predicate for testing based on Event type.
   * @param type name of the event type to filter (case-insensitive).
   * @return predicate performing the evaluation
   */
  public static PredicateKV eventTypeIs(String type) {
    return kv -> {
      if (kv == null || kv.getValue() == null)
        return false;

      return kv.getValue().getType().equalsIgnoreCase(type);
    };
  }

  public static PredicateKV actorTypeIs(String type) {
    return kv -> {
      if (kv == null || kv.getValue() == null)
        return false;

      return kv.getValue().getActor().getType().equalsIgnoreCase(type);
    };
  }

  public static ProcessFunction<Iterable<Event>, Event> eventPerSec() {
    return list -> {
      return Event.EMPTY_EVENT;
    };
  }

}
