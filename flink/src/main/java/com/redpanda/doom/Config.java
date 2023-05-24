package com.redpanda.doom;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Config implements Map<String, Object> {
  public static final String KEY_BROKERS = "brokers";
  public static final String KEY_USER = "user";
  public static final String KEY_PASSWORD = "password";
  public static final String KEY_SASL_MECHANISM = "saslMechanism";
  public static final String KEY_TLS = "tls";
  public static final String KEY_TOPICS = "topics";
  public static final String KEY_GROUP_ID = "groupId";
  public static final String KEY_CLIENT_ID_PREFIX = "clientIdPrefix";
  public static final String KEY_GROUP_INSTANCE_ID = "groupInstanceId";

  private final Map<String, Object> map;
  private final Map<String, Object> defaults = Map.ofEntries(
      Map.entry(KEY_BROKERS, "localhost:9092"),
      Map.entry(KEY_USER, "doom"),
      Map.entry(KEY_PASSWORD, "doom"),
      Map.entry(KEY_SASL_MECHANISM, "plain"),
      Map.entry(KEY_TLS, false),
      Map.entry(KEY_TOPICS, "doom"),
      Map.entry(KEY_GROUP_ID, "doom"),
      Map.entry(KEY_CLIENT_ID_PREFIX, "flinkyboi"),
      Map.entry(KEY_GROUP_INSTANCE_ID, "flinkyboi")
  );

  public Config(final String[] args) {
    final ArgumentParser parser = ArgumentParsers
        .newFor(DoomPipeline.class.getName())
        .build()
        .defaultHelp(true)
        .description("A Flink pipeline!");

    parser.addArgument("--brokers")
        .dest(KEY_BROKERS)
        .nargs("?")
        .help("Redpanda broker uri(s)");
    parser.addArgument("--user")
        .nargs("?")
        .dest(KEY_USER)
        .help("Username");
    parser.addArgument("--password")
        .dest(KEY_PASSWORD)
        .nargs("?")
        .help("Password");
    parser.addArgument("--sasl-mechanism")
        .dest(KEY_SASL_MECHANISM)
        .dest("saslMechanism")
        .nargs("?")
        .choices("SCRAM-SHA-256", "SCRAM-SHA-512", "PLAIN")
        .help("SASL mechanism to use");
    parser.addArgument("--tls")
        .dest(KEY_TLS)
        .action(Arguments.storeTrue())
        .setDefault(false)
        .help("Use TLS?");

    Namespace ns = null;
    try {
      ns = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.exit(1);
    }

    map = new HashMap<>();
    ns.getAttrs().forEach((k, v) -> {
      if (v != null)
        map.put(k, v);
    });
  }

  private String withNamespace(String keyName) {
    return DoomPipeline.class.getPackage().getName() + "." + keyName;
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  @Override
  public Object get(Object key) {
    if (key == null)
      return null;

    if (map.containsKey(key))
      return map.get(key);
    if (System.getProperties().containsKey(key))
      return System.getProperty(withNamespace(key.toString()), null);
    return defaults.getOrDefault(key, null);
  }

  public String getString(String key) {
    final Object value = get(key);
    if (value != null)
      return value.toString();
    return null;
  }

  public boolean getBoolean(String key) {
    final Object value = get(key);
    if (value != null)
      return Boolean.parseBoolean(value.toString());
    return false;
  }

  @Override
  public Object put(String key, Object value) {
    throw new RuntimeException("unimplemented");
  }

  @Override
  public Object remove(Object key) {
    throw new RuntimeException("unimplemented");
  }

  @Override
  public void putAll(Map<? extends String, ?> m) {
    throw new RuntimeException("unimplemented");
  }

  @Override
  public void clear() {
    throw new RuntimeException("unimplemented");
  }

  @Override
  public Set<String> keySet() {
    return map.keySet();
  }

  @Override
  public Collection<Object> values() {
    return map.values();
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return map.entrySet();
  }

  @Override
  public Object getOrDefault(Object key, Object defaultValue) {
    final Object value = this.get(key);
    if (value == null)
      return defaultValue;
    return value;
  }
}
