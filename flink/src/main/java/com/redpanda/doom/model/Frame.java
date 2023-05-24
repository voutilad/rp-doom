package com.redpanda.doom.model;

import java.io.Serializable;
import java.util.Objects;

public class Frame implements Serializable {

  /* Non-null placeholder for an empty frame. */
  public static final Frame EMPTY_FRAME = new Frame();

  /* Zero-arg construct for Gson support. */
  public Frame() {
    this.millis = Long.MAX_VALUE;
    this.tic = Integer.MAX_VALUE;
  }

  public Frame(long millis, int tic) {
    this.millis = millis;
    this.tic = tic;
  }

  private long millis;

  private int tic;

  public long getMillis() {
    return millis;
  }

  public int getTic() {
    return tic;
  }

  public void setMillis(long millis) {
    this.millis = millis;
  }

  public void setTic(int tic) {
    this.tic = tic;
  }

  @Override
  public String toString() {
    if (this.equals(EMPTY_FRAME))
      return "Frame{EMPTY}";

    return "Frame{" +
        "millis=" + millis +
        ", tic=" + tic +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Frame frame = (Frame) o;
    return millis == frame.millis && tic == frame.tic;
  }

  @Override
  public int hashCode() {
    return Objects.hash(millis, tic);
  }
}
