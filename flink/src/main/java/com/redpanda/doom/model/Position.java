package com.redpanda.doom.model;

import java.io.Serializable;
import java.util.Objects;

public class Position implements Serializable {

  /* Non-null placeholder for empty or unset position value. */
  public static final Position EMPTY_POSITION = new Position();

  private Position() {
    this.x = Long.MAX_VALUE;
    this.y = Long.MAX_VALUE;
    this.z = Long.MAX_VALUE;
    this.angle = Long.MAX_VALUE;
    this.subsector = Long.MAX_VALUE;
  }

  public Position(long x, long y, long z, long angle, long subsector) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.angle = angle;
    this.subsector = subsector;
  }

  private long x;

  private long y;

  private long z;

  private long angle;

  private long subsector;

  public long getX() {
    return x;
  }

  public long getY() {
    return y;
  }

  public long getZ() {
    return z;
  }

  public long getAngle() {
    return angle;
  }

  public long getSubsector() {
    return subsector;
  }

  @Override
  public String toString() {
    if (this.equals(EMPTY_POSITION))
      return "Position{EMPTY}";

    return "Position{" +
        "x=" + x +
        ", y=" + y +
        ", z=" + z +
        ", angle=" + angle +
        ", subsector=" + subsector +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Position position = (Position) o;
    return x == position.x && y == position.y && z == position.z && angle == position.angle && subsector == position.subsector;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y, z, angle, subsector);
  }
}
