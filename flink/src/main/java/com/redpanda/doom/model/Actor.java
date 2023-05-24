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
  public Actor() {
    this.position = Position.EMPTY_POSITION;
    this.type = "UNKNOWN_TYPE";
    this.health = Integer.MAX_VALUE;
    this.armor = Integer.MAX_VALUE;
    this.id = Long.MAX_VALUE;
  }

  public Actor(Position position, String type, int health, int armor, long id) {
    this.position = position;
    this.type = type;
    this.health = health;
    this.armor = armor;
    this.id = id;
  }

  public void setPosition(Position position) {
    this.position = position;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setHealth(int health) {
    this.health = health;
  }

  public void setArmor(int armor) {
    this.armor = armor;
  }

  public void setId(long id) {
    this.id = id;
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
