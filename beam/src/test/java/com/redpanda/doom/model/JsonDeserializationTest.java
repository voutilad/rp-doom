package com.redpanda.doom.model;

import org.junit.Assert;
import org.junit.Test;

public class JsonDeserializationTest {

  @Test
  public void testWeCanDeserializeAnEvent() {
    final String json = "{ " +
        "\"session\": \"abc123\", " +
        "\"counter\": 123, " +
        "\"type\": \"move\", " +
        "\"frame\": { \"tic\": 123, \"millis\": 456 }, " +
        "\"actor\": { \"type\": \"player\", \"health\": 25, \"armor\": 40, \"id\": 4444 } " +
    "}";
    final Event event = Event.fromJson(json);

    Assert.assertNotNull(event);
    Assert.assertNotEquals(Event.EMPTY_EVENT, event);
    Assert.assertEquals("abc123", event.getSession());
    Assert.assertEquals(123, event.getCounter());

    Assert.assertNotNull(event.getFrame());
    Assert.assertNotEquals(Frame.EMPTY_FRAME, event.getFrame());
    Assert.assertEquals(123, event.getFrame().getTic());
    Assert.assertEquals(456, event.getFrame().getMillis());

    System.out.println("Got event: " + event);
  }
}
