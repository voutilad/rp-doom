package com.redpanda.doom.fn;

import com.redpanda.doom.model.Actor;
import com.redpanda.doom.model.Event;
import org.apache.flink.api.common.functions.FilterFunction;

public class FilterActorType implements FilterFunction<Event> {
  private final String actorType;

  public FilterActorType(String actorType) {
    this.actorType = actorType;
  }

  public static FilterFunction<Event> of(String actorType) {
    return new FilterActorType(actorType);
  }
  @Override
  public boolean filter(Event value) throws Exception {
    if (value == null)
      return false;
    final Actor actor = value.getActor();
    if (actor == null)
      return false;
    return actor.getType().equalsIgnoreCase(actorType);
  }
}
