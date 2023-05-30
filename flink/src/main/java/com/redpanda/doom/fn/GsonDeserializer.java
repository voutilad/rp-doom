package com.redpanda.doom.fn;

import com.google.gson.Gson;
import com.redpanda.doom.model.Event;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;

public class GsonDeserializer extends RichMapFunction<String, Event> {

  private transient Gson gson = null;

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    gson = new Gson();
  }

  @Override
  public Event map(String json) {
    return gson.fromJson(json, Event.class);
  }
}
