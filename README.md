# CS6650 Assignment 4 — Distributed Key-Value Store with Replication

**Northeastern University — CS6650 Building Scalable Distributed Systems**

---

## Overview

This project implements two distributed in-memory Key-Value (KV) store architectures to explore the trade-offs described by the CAP theorem under configurable replication strategies.

**Leader-Follower Database** — A single designated Leader accepts all writes and replicates to Follower nodes. Supports three quorum configurations: W=5/R=1, W=1/R=5, and W=3/R=3.

**Leaderless Database** — Any node may accept reads or writes. The node receiving a write becomes the Write Coordinator and propagates to all peers (W=N=5, R=1).

Both systems consist of five nodes (N=5) and are deployed on AWS EC2 with Terraform-managed infrastructure.

---

## Team

| Name | Module |
|---|---|
| Yunhong Huang | Load Tester |
| Qingyu Cheng | KV Node Core & Leader-Follower Logic |
| Runxin Shao | Leaderless Logic & Unit Tests |
| Ziqi Yang | Infrastructure, Deployment & Report |

---

## Architecture

### Leader-Follower

```
                    ┌─────────────┐
      Writes ──────▶│   Leader    │
                    │  (Node 1)   │
                    └──────┬──────┘
               ┌───────────┼───────────┐
               ▼           ▼           ▼
          ┌────────┐  ┌────────┐  ┌────────┐
          │ Follow │  │ Follow │  │ Follow │
          │  er 2  │  │  er 3  │  │  er 4  │  ...
          └────────┘  └────────┘  └────────┘
               ▲
      Reads ───┘ (any node, round-robin or ALB)
```

### Leaderless

```
            ┌─────────────────────┐
            │  Application Load   │
            │      Balancer       │
            └──────────┬──────────┘
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
    ┌─────────┐   ┌─────────┐   ┌─────────┐
    │ Node 1  │◀─▶│ Node 2  │◀─▶│ Node 3  │  ...
    │(Coord.) │   │         │   │         │
    └─────────┘   └─────────┘   └─────────┘
```

---

## API Reference

All nodes expose the following endpoints:

| Method | Path | Description |
|---|---|---|
| `PUT` | `/kv` | Write a key-value pair |
| `GET` | `/kv?key={key}` | Read a value by key |
| `GET` | `/local_read?key={key}` | Read from this node only (test use) |

### PUT /kv

**Request**
```json
{ "key": "mykey", "value": "myvalue" }
```

**Response — 201 Created**
```json
{ "key": "mykey", "version": 1 }
```

### GET /kv?key=mykey

**Response — 200 OK**
```json
{ "key": "mykey", "value": "myvalue", "version": 1 }
```

**Response — 404 Not Found** (key does not exist)

### GET /local_read?key=mykey

Returns the value stored on the receiving node only, bypassing any quorum or leader-routing logic. Intended for consistency testing.

---

## Replication Strategies

### Leader-Follower

| Configuration | Write Behavior | Read Behavior | Total Write Latency |
|---|---|---|---|
| W=5, R=1 | Leader sequentially updates all 4 Followers before responding | Leader reads locally | ~1000ms |
| W=1, R=5 | Leader responds after writing locally only | Leader sequentially collects from all 4 Followers, returns highest version | ~250ms write / ~250ms read |
| W=3, R=3 | Leader updates 2 Followers before responding | Leader collects from 2 Followers, returns highest version | ~600ms |

### Leaderless

| Configuration | Write Behavior | Read Behavior |
|---|---|---|
| W=5, R=1 | Write Coordinator propagates to all 4 peers sequentially | Node reads and returns its local value |

### Simulated Delays

To expose replication inconsistency windows, artificial delays are applied at every node:

- **Write** — Each node sleeps **200ms** before writing and responding
- **Read** — Each node sleeps **50ms** before responding

---

## Project Structure

```
cs6650-assignment4/
├── kv-node/                  # Spring Boot KV service (shared by all nodes)
│   ├── src/
│   │   ├── main/java/
│   │   │   ├── controller/   # KVController (PUT /kv, GET /kv, GET /local_read)
│   │   │   ├── service/      # LeaderService, FollowerService, LeaderlessService
│   │   │   └── model/        # KVEntry, KVRequest, KVResponse
│   │   └── resources/
│   │       └── application.yml
│   ├── Dockerfile
│   └── pom.xml
├── load-tester/              # Modified load tester from Assignment 2
│   └── src/
├── tests/                    # Unit and integration tests
├── terraform/                # AWS infrastructure definitions
│   ├── main.tf
│   ├── variables.tf
│   └── outputs.tf
├── docker-compose.yml        # Local 5-node test environment
└── README.md
```

---

## Configuration

Nodes are configured entirely via environment variables:

### Leader-Follower

| Variable | Description | Example |
|---|---|---|
| `ROLE` | Node role | `leader` or `follower` |
| `FOLLOWER_URLS` | Comma-separated follower addresses (leader only) | `http://node2:8080,http://node3:8080,...` |
| `WRITE_QUORUM_SIZE` | W value | `1`, `3`, or `5` |
| `READ_QUORUM_SIZE` | R value | `1`, `3`, or `5` |

### Leaderless

| Variable | Description | Example |
|---|---|---|
| `PEER_URLS` | Comma-separated addresses of all other nodes | `http://node2:8080,http://node3:8080,...` |

---

## Running Locally

### Prerequisites

- Docker & Docker Compose
- Java 17
- Maven

### Start a 5-node cluster

```bash
docker-compose up --build
```

This starts one leader on port `8080` and four followers on ports `8081`–`8084`.

### Example requests

```bash
# Write
curl -X PUT http://localhost:8080/kv \
  -H "Content-Type: application/json" \
  -d '{"key": "foo", "value": "bar"}'

# Read
curl http://localhost:8080/kv?key=foo

# Local read (bypass routing)
curl http://localhost:8081/local_read?key=foo
```

---

## Running Tests

```bash
cd kv-node
mvn test
```

| Test | Configuration | Expected Outcome |
|---|---|---|
| `testStrongConsistencyLeader` | W=5, R=1 | Read from Leader returns new value after write |
| `testStrongConsistencyFollower` | W=5, R=1 | Read from any Follower returns new value after write |
| `testInconsistencyWindow` | W=1, R=1 | `local_read` on Followers within update window returns stale data |
| `testLeaderlessInconsistency` | W=5, R=1 | Read from non-Coordinator within update window returns stale data |

---

## Infrastructure

All AWS resources are defined in Terraform under `terraform/`.

### Resources

- **6 × EC2 (t3.micro)** — 5 database nodes + 1 load tester instance
- **1 × ALB** — In front of all 5 nodes for the Leaderless configuration
- **Security Groups** — Inter-node HTTP, load tester → nodes

### Deploy

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

### Tear Down

```bash
terraform destroy
```

> ⚠️ Remember to run `terraform destroy` or stop all EC2 instances when not in use to avoid unnecessary AWS charges.

---

## Load Testing

The load tester is modified from Assignment 2 and supports configurable read/write ratios with stale-read detection.

### Key design choices

- **Small key space** (≤ 500 keys) — ensures reads and writes to the same key are clustered closely in time, maximizing stale read capture
- **Version tracking** — the client records the last written version per key and flags any read returning a lower version as stale
- **Separate latency recording** — read and write latencies are logged independently for distribution analysis

### Usage

```bash
cd load-tester
mvn exec:java -Dexec.args="--target http://<leader-ip>:8080 --threads 32 --duration 60 --write-ratio 0.1"
```

### Read/Write Ratios Tested

| Writes | Reads |
|---|---|
| 1% | 99% |
| 10% | 90% |
| 50% | 50% |
| 90% | 10% |

Results are output as CSV and include per-request latency, version, staleness flag, and timestamp.

---

## Error Handling

- If a node is unreachable during replication, the error is logged and **503 Service Unavailable** is returned to the client.
- No retry logic, failure detection, or node recovery is implemented — this is an experimental system.