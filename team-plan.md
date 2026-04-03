# CS6650 Assignment 4 Team Plan

## 1. Architecture Summary

### 1.1 System Modes

| Dimension | Leader-Follower | Leaderless |
|---|---|---|
| Node count | 5 nodes with 1 leader and 4 followers | 5 peer nodes |
| Write entry point | Leader only | Any node via load balancer |
| Read entry point | Any node | Any node via load balancer |
| Version source | Leader | Write coordinator |
| Required configurations | W=5 R=1, W=1 R=5, W=3 R=3 | W=5 R=1 |

### 1.2 Delay Model

- Each write request must wait 200 ms before response.
- Each read request on `/kv` and `/local_read` must wait 50 ms before response.
- With sequential replication, W=5 yields approximately 1000 ms write latency.

### 1.3 Core Data Model

```java
record KVEntry(String value, int version) {}
ConcurrentHashMap<String, KVEntry> store = new ConcurrentHashMap<>();
```

## 2. Workstreams and Ownership

### 2.1 Module 0: Load Tester

Owner: Yunhong Huang

Scope:

1. Upgrade the Assignment 2 load tester as the baseline implementation.
2. Restrict key space to 100 to 500 keys to increase same-key contention.
3. Support write ratios of 1 percent, 10 percent, 50 percent, and 90 percent.
4. Implement stale-read detection with last written version per key.
5. Route Leader-Follower writes to leader and reads to all nodes with round-robin.
6. Route Leaderless reads and writes to ALB endpoint.
7. Collect read and write latency separately for every request.
8. Collect stale-read count and stale-read ratio.
9. Collect same-key read-write time interval data.
10. Export output in CSV or JSON format.

Required result record:

```java
record RequestResult(String type, String key, long latencyMs,
                     int version, boolean isStale, long timestamp) {}
```

### 2.2 Module A: KV Node Core and Leader-Follower

Owner: Qingyu Cheng

Scope:

1. Implement thread-safe in-memory KV storage using `ConcurrentHashMap`.
2. Implement `PUT /kv` including write delay logic.
3. Implement `GET /kv` including read delay logic.
4. Implement `GET /local_read` with local-only read semantics.
5. Implement leader write path with sequential replication up to configured W.
6. Implement leader read path using configured R and highest-version return.
7. Implement follower handlers for internal forwarded requests.
8. Support `ROLE`, `FOLLOWER_URLS`, `WRITE_QUORUM_SIZE`, and `READ_QUORUM_SIZE`.
9. Guarantee atomic version allocation on the leader.
10. Preserve strictly sequential replication to maintain expected latency behavior.

### 2.3 Module B: Leaderless and Tests

Owner: Runxin Shao

Scope:

1. Implement leaderless write coordinator behavior for W=5.
2. Assign version at coordinator and apply local write with 200 ms delay.
3. Forward writes sequentially to all peers and wait for all acknowledgments.
4. Return success only after full replication acknowledgment.
5. Implement leaderless read behavior with local read and 50 ms delay.
6. Support `PEER_URLS` runtime configuration.
7. Implement Leader-Follower Test A for W=5 leader read consistency.
8. Implement Leader-Follower Test B for W=5 follower read consistency.
9. Implement Leader-Follower Test C for W=1 stale local read during propagation window.
10. Implement Leaderless stale-read test during coordinator propagation window.
11. Implement Leaderless post-ack consistency test on coordinator.
12. Implement Leaderless post-ack consistency test on peer nodes.
13. Build tests with `@SpringBootTest` and `TestRestTemplate`.
14. Trigger stale-window reads immediately and concurrently to avoid false negatives.

### 2.4 Module C: Infrastructure and Report

Owner: Ziqi Yang

Scope:

1. Provide one Docker image for all roles with environment-driven behavior.
2. Provide `docker-compose.yml` for local five-node execution.
3. Provision five KV node EC2 instances with Terraform.
4. Provision one load tester EC2 instance with Terraform.
5. Provision ALB and target group for Leaderless deployment.
6. Configure security groups for node-to-node and tester-to-node access.
7. Ensure Spring Boot runtime configuration prioritizes environment variables.
8. Execute each required configuration for 10 minutes in final experiments.
9. Produce read latency distribution plots.
10. Produce write latency distribution plots.
11. Produce same-key read-write interval distribution plots.
12. Document consistency and latency tradeoff analysis in the final report.

## 3. API Contract

| Method | Endpoint | Request | Response |
|---|---|---|---|
| PUT | `/kv` | `{"key":"k","value":"v"}` | `201 {"key":"k","version":1}` |
| GET | `/kv?key=k` | none | `200 {"key":"k","value":"v","version":1}` or `404` |
| GET | `/local_read?key=k` | none | `200 {"key":"k","value":"v","version":1}` or `404` |
| PUT | `/kv/internal` | internal replication payload | `200` |

## 4. Delivery Sequence

1. Complete Module 0 first to establish benchmark and staleness measurement capability.
2. Execute Module A and Module C in parallel after Module 0 baseline is ready.
3. Start Module B after Module A API and service interfaces are stable.
4. Run integration tests and load experiments across all required configurations.
5. Finalize report artifacts and complete submission package.

## 5. Collaboration Rules

1. Use `main` branch with path-based ownership to minimize merge conflicts.
2. Assign `load-tester/` ownership to Module 0.
3. Assign `kv-node/` ownership to Modules A and B.
4. Assign `terraform/` ownership to Module C.
5. Run `git pull --rebase` before every push.
6. Do not modify shared API contracts without team agreement.

## 6. Risks and Controls

1. Risk: parallel replication could invalidate expected quorum latency behavior.
2. Control: enforce sequential replication and verify end-to-end latency in tests.
3. Risk: non-atomic version increments could cause incorrect ordering.
4. Control: use thread-safe per-key version counters.
5. Risk: stale-window tests could miss inconsistency due to timing drift.
6. Control: issue immediate concurrent reads during propagation windows.
7. Risk: large key space could reduce stale-read observability.
8. Control: cap key space at 500 keys.
