package com.redpanda.doom.model;

import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.CoderException;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class EventCoder extends Coder<Event> {
  @Override
  public void encode(Event value, @UnknownKeyFor @NonNull @Initialized OutputStream outStream) throws @UnknownKeyFor@NonNull@Initialized CoderException, @UnknownKeyFor@NonNull@Initialized IOException {

  }

  @Override
  public Event decode(@UnknownKeyFor @NonNull @Initialized InputStream inStream) throws @UnknownKeyFor@NonNull@Initialized CoderException, @UnknownKeyFor@NonNull@Initialized IOException {
    return null;
  }

  @Override
  public @UnknownKeyFor @NonNull @Initialized List<? extends @UnknownKeyFor @NonNull @Initialized Coder<@UnknownKeyFor @NonNull @Initialized ?>> getCoderArguments() {
    return null;
  }

  @Override
  public void verifyDeterministic() throws @UnknownKeyFor@NonNull@Initialized NonDeterministicException {

  }
}
