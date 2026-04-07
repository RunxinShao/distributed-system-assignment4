
package edu.neu.cs6650.assignment4.loadtester;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

public class LoadTesterApplication {

  // One record per request
  record RequestRecord(
      long timestampMs,
      String type,       // "READ" or "WRITE"
      String key,
      long latencyMs,
      int version,
      boolean stale      // always false for writes
  ) {}

  public static void main(String[] args) throws Exception {
    LoadTesterConfig config = LoadTesterConfig.fromArgs(args);

    System.out.println("CS6650 Assignment 4 Load Tester");
    System.out.printf(
        "target=%s, threads=%d, duration=%ds, writeRatio=%.2f%n",
        config.target(), config.threads(),
        config.durationSeconds(), config.writeRatio());

    // Thread-safe result list
    ConcurrentLinkedQueue<RequestRecord> results = new ConcurrentLinkedQueue<>();

    ExecutorService executor = Executors.newFixedThreadPool(config.threads());
    long endTime = System.currentTimeMillis() + config.durationSeconds() * 1000L;
    Random rand = new Random();

    ConcurrentHashMap<String, Integer> lastWrittenVersion = new ConcurrentHashMap<>();
    AtomicInteger staleCount = new AtomicInteger(0);

    for (int i = 0; i < config.threads(); i++) {
      executor.submit(() -> {
        while (System.currentTimeMillis() < endTime) {
          try {
            String key = "key" + rand.nextInt(100);
            boolean isWrite = rand.nextDouble() < config.writeRatio();
            long start = System.currentTimeMillis();
            int version = 0;
            boolean stale = false;

            if (isWrite) {
              URL url = new URL(config.target() + "/kv");
              HttpURLConnection conn = (HttpURLConnection) url.openConnection();
              conn.setRequestMethod("PUT");
              conn.setDoOutput(true);
              conn.setRequestProperty("Content-Type", "application/json");

              String body = "{\"key\":\"" + key + "\",\"value\":\"val\"}";
              try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
              }

              if (conn.getResponseCode() == 201) {
                version = extractVersion(readResponse(conn));
                lastWrittenVersion.put(key, version);
              }
              conn.disconnect();

            } else {
              URL url = new URL(config.target() + "/kv?key=" + key);
              HttpURLConnection conn = (HttpURLConnection) url.openConnection();
              conn.setRequestMethod("GET");

              if (conn.getResponseCode() == 200) {
                version = extractVersion(readResponse(conn));
                int expected = lastWrittenVersion.getOrDefault(key, 0);
                if (version < expected) {
                  stale = true;
                  staleCount.incrementAndGet();
                }
              }
              conn.disconnect();
            }

            long latency = System.currentTimeMillis() - start;
            System.out.printf("%s latency=%dms%n", isWrite ? "WRITE" : "READ", latency);

            results.add(new RequestRecord(
                start, isWrite ? "WRITE" : "READ", key, latency, version, stale));

          } catch (Exception e) {
            System.out.println("Request failed: " + e.getMessage());
          }
        }
      });
    }

    executor.shutdown();
    executor.awaitTermination(config.durationSeconds() + 5, TimeUnit.SECONDS);

    System.out.println("Test finished.");
    System.out.println("Stale reads: " + staleCount.get());

    // ── CSV Export ──────────────────────────────────────────────────────────
    // File name encodes config so multiple runs don't overwrite each other
    String csvFile = String.format("results_wr%.0f_threads%d.csv",
        config.writeRatio() * 100, config.threads());

    try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile))) {
      pw.println("timestamp_ms,type,key,latency_ms,version,stale");
      for (RequestRecord r : results) {
        pw.printf("%d,%s,%s,%d,%d,%b%n",
            r.timestampMs(), r.type(), r.key(),
            r.latencyMs(), r.version(), r.stale());
      }
    }
    System.out.println("CSV saved to: " + csvFile);
  }

  private static String readResponse(HttpURLConnection conn) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) sb.append(line);
      return sb.toString();
    }
  }

  private static int extractVersion(String json) {
    try {
      int idx = json.indexOf("\"version\":");
      if (idx == -1) return 0;
      String sub = json.substring(idx + 10).trim();
      return Integer.parseInt(sub.split("[,}]")[0].trim());
    } catch (Exception e) {
      return 0;
    }
  }
}