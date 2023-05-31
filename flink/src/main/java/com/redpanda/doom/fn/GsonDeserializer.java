package com.redpanda.doom.fn;

import com.google.gson.Gson;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;

public class GsonDeserializer<T> extends RichMapFunction<String, T> {

  private transient Gson gson = null;
  private final Class<T> classOfT;

  public GsonDeserializer(Class<T> classOfT) {
    this.classOfT = classOfT;
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    gson = new Gson();
  }

  @Override
  public T map(String json) {
    return gson.fromJson(json, classOfT);
  }
}
