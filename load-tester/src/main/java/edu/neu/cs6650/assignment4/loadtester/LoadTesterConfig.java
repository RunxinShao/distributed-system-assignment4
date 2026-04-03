package edu.neu.cs6650.assignment4.loadtester;

import java.util.HashMap;
import java.util.Map;

public record LoadTesterConfig(
    String target,
    int threads,
    int durationSeconds,
    double writeRatio
) {

  public static LoadTesterConfig fromArgs(String[] args) {
    Map<String, String> parsed = parseArgs(args);
    String target = parsed.getOrDefault("target", "http://localhost:8080");
    int threads = Integer.parseInt(parsed.getOrDefault("threads", "32"));
    int durationSeconds = Integer.parseInt(parsed.getOrDefault("duration", "60"));
    double writeRatio = Double.parseDouble(parsed.getOrDefault("write-ratio", "0.1"));
    return new LoadTesterConfig(target, threads, durationSeconds, writeRatio);
  }

  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> values = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (!arg.startsWith("--")) {
        continue;
      }
      String key = arg.substring(2);
      if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
        values.put(key, args[i + 1]);
        i++;
      }
    }
    return values;
  }
}
