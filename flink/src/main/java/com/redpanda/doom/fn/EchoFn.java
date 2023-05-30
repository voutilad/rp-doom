package com.redpanda.doom.fn;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoFn<T> extends RichMapFunction<T, T> {
  private transient Logger logger = null;

  @Override
  public void open(Configuration parameters) {
    logger = LoggerFactory.getLogger(this.getClass());
  }

  @Override
  public T map(T value) {
    final String s = value == null ? "null" : value.toString();
    logger.info(s);
    return value;
  }
}
