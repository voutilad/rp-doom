package com.redpanda.doom.dofn;

import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Echo extends DoFn<Object, Object> {

  private static final Logger logger = LoggerFactory.getLogger(Echo.class);

  private final String prefix;
  public Echo(String prefix) {
    this.prefix = prefix;
  }

  public Echo() {
    this.prefix = "";
  }

  @ProcessElement
  public void processElement(ProcessContext context) {
    final var element = context.element();
    logger.info(prefix + element);
    context.output(element);
  }
}
