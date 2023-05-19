package com.redpanda.doom.dofn;

import com.redpanda.doom.model.Event;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.vendor.grpc.v1p48p1.com.google.gson.Gson;

public class EventDeserializer extends DoFn<String, KV<String, Event>> {
  private transient Gson gson;
  @Setup
  public void setUp() {
    gson = new Gson();
  }

  @ProcessElement
  public void processElement(ProcessContext context) {
    final String json = context.element();
    final Event event = gson.fromJson(json, Event.class);
    context.output(KV.of(event.getSession(), event));
  }
}
