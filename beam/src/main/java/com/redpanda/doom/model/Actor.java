package com.redpanda.doom.model;

import java.io.Serializable;
import java.util.Objects;

public class Actor implements Serializable {

  public static final Actor EMPTY_ACTOR = new Actor();

  /* Position of Actor at time of event. */
  private Position position;

  /* Type of actor, e.g. Player. */
  private String type;

  private int health;
  private int armor;

  /* Unique in-game id for the Actor instance. */
  private long id;

  /* Zero-arg constructor for Gson support. */
  private Actor() {
    this.position = Position.EMPTY_POSITION;
    this.type = "UNKNOWN_TYPE";
    this.health = Integer.MAX_VALUE;
    this.armor = Integer.MAX_VALUE;
    this.id = Long.MAX_VALUE;
  }

  public Position getPosition() {
    return position;
  }

  public String getType() {
    return type;
  }

  public int getHealth() {
    return health;
  }

  public int getArmor() {
    return armor;
  }

  public long getId() {
    return id;
  }

  @Override
  public String toString() {
    if (this.equals(EMPTY_ACTOR))
      return "Actor{EMPTY}";

    return "Actor{" +
        "position=" + position +
        ", type='" + type + '\'' +
        ", health=" + health +
        ", armor=" + armor +
        ", id=" + id +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Actor actor = (Actor) o;
    return health == actor.health && armor == actor.armor && id == actor.id && Objects.equals(position, actor.position) && Objects.equals(type, actor.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(position, type, health, armor, id);
  }
}
