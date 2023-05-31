package com.redpanda.doom.model;

import com.google.gson.Gson;

import java.io.Serializable;

public class Metric implements Serializable {
  /* Unique session identifier. */
  private String session;

  private String metric;

  private Double value;

  private final transient Gson gson;

  public Metric() {
    this("UNKNOWN_SESSION", "UNKNOWN_METRIC", Double.NaN);
  }

  public Metric(String session, String metric, Double value) {
    this.session = session;
    this.metric = metric;
    this.value = value;

    gson = new Gson();
  }

  @Override
  public String toString() {
    return gson.toJson(this);
  }

  public String getSession() {
    return session;
  }

  public void setSession(String session) {
    this.session = session;
  }

  public String getMetric() {
    return metric;
  }

  public void setMetric(String metric) {
    this.metric = metric;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }
}
