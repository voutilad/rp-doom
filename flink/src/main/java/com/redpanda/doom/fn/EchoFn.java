package com.redpanda.doom.fn;

import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoFn<T> implements MapFunction<T, T> {
  final transient Logger logger = LoggerFactory.getLogger(EchoFn.class);

  @Override
  public T map(T value) throws Exception {
    final String s = value == null ? "null" : value.toString();
    logger.info(s);
    return value;
  }
}
