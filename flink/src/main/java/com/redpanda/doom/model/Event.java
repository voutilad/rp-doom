package com.redpanda.doom.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * A base event emitted by Doom during gameplay.
 */
public class Event implements Serializable {
  public static final Event EMPTY_EVENT = new Event();

  /* Unique session identifier. */
  private String session;

  /* Frame counter. */
  private long counter;

  /* Event type. */
  private String type;

  /* Frame. */
  private Frame frame;

  /* Primary actor producing the event. */
  private Actor actor;

  /* Optional target of the event. */
  private Actor target;

  /* Zero-arg constructor for Gson support. */
  public Event() {
    this.session = "UNKNOWN_SESSION";
    this.counter = Long.MAX_VALUE;
    this.type = "UNKNOWN_TYPE";
    this.frame = Frame.EMPTY_FRAME;
    this.actor = Actor.EMPTY_ACTOR;
    this.target = Actor.EMPTY_ACTOR;
  }

  public Event(String session, long counter, String type, Frame frame, Actor actor, Actor target) {
    this.session = session;
    this.counter = counter;
    this.type = type;
    this.frame = frame;
    this.actor = actor;
    this.target = target;
  }

  public String getSession() {
    return session;
  }

  public long getCounter() {
    return counter;
  }

  public String getType() {
    return type;
  }

  public Frame getFrame() {
    return frame;
  }

  public Actor getActor() {
    return actor;
  }

  public Actor getTarget() {
    return target;
  }

  @Override
  public String toString() {
    if (this.equals(EMPTY_EVENT))
      return "Event{EMPTY}";

    return "Event{" +
        "session='" + session + '\'' +
        ", counter=" + counter +
        ", type='" + type + '\'' +
        ", frame=" + frame +
        ", actor=" + actor +
        ", target=" + target +
        '}';
  }

  public void setSession(String session) {
    this.session = session;
  }

  public void setCounter(long counter) {
    this.counter = counter;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setFrame(Frame frame) {
    this.frame = frame;
  }

  public void setActor(Actor actor) {
    this.actor = actor;
  }

  public void setTarget(Actor target) {
    this.target = target;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Event event = (Event) o;
    return counter == event.counter && Objects.equals(session, event.session) && Objects.equals(type, event.type) && Objects.equals(frame, event.frame) && Objects.equals(actor, event.actor) && Objects.equals(target, event.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(session, counter, type, frame, actor, target);
  }
}
