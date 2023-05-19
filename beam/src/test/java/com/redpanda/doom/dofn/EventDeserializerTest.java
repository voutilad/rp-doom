package com.redpanda.doom.dofn;

import com.redpanda.doom.model.Actor;
import com.redpanda.doom.model.Event;
import com.redpanda.doom.model.Frame;
import com.redpanda.doom.model.Position;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;

public class EventDeserializerTest {

  @Rule
  public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testEventDeserializer() {
    final String json = "{ " +
        "\"session\": \"abc123\", " +
        "\"counter\": 123, " +
        "\"type\": \"move\", " +
        "\"frame\": { \"tic\": 123, \"millis\": 456 }, " +
        "\"actor\": { \"type\": \"player\", \"health\": 25, \"armor\": 40, \"id\": 4444 } " +
        "}";
    final Event event = new Event("abc123", 123, "move", new Frame(456, 123),
        new Actor(Position.EMPTY_POSITION, "player", 25, 40, 4444), Actor.EMPTY_ACTOR);
    final KV<String, Event> expected = KV.of("abc123", event);
    PCollection<String> input = pipeline.apply(Create.of(json));
    PCollection<KV<String, Event>> output = input.apply(ParDo.of(new EventDeserializer()));

    PAssert.that(output).containsInAnyOrder(expected);

    pipeline.run();
  }
}
