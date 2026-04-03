package edu.neu.cs6650.assignment4.loadtester;

public class LoadTesterApplication {

  public static void main(String[] args) {
    LoadTesterConfig config = LoadTesterConfig.fromArgs(args);
    System.out.println("CS6650 Assignment 4 Load Tester Skeleton");
    System.out.printf(
        "target=%s, threads=%d, duration=%ds, writeRatio=%.2f%n",
        config.target(),
        config.threads(),
        config.durationSeconds(),
        config.writeRatio());
    System.out.println("TODO: implement workload generation, stale-read detection, and CSV/JSON output.");
  }
}
