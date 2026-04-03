# CS6650 Assignment 4 - Distributed Key-Value Store with Replication

**Northeastern University - CS6650 Building Scalable Distributed Systems**

---

## Overview

This project implements two distributed in-memory key-value store architectures to evaluate CAP-related trade-offs under configurable replication strategies.

**Leader-Follower database**: A designated leader accepts all writes and replicates updates to follower nodes. It supports three quorum configurations: W=5/R=1, W=1/R=5, and W=3/R=3.

**Leaderless database**: Any node can accept reads and writes. The node that receives a write becomes the write coordinator and propagates the update to all peers with W=N=5 and R=1.

Both systems use five nodes with N=5 and are deployed on AWS EC2 with Terraform-managed infrastructure.

---

## Team

| Name | Module |
|---|---|
| Yunhong Huang | Load Tester |
| Qingyu Cheng | KV Node Core and Leader-Follower Logic |
| Runxin Shao | Leaderless Logic and Unit Tests |
| Ziqi Yang | Infrastructure, Deployment, and Report |

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
| `GET` | `/local_read?key={key}` | Read from the local node only for testing |

### PUT /kv

**Request**
```json
{ "key": "mykey", "value": "myvalue" }
```

**Response (201 Created)**
```json
{ "key": "mykey", "version": 1 }
```

### GET /kv?key=mykey

**Response (200 OK)**
```json
{ "key": "mykey", "value": "myvalue", "version": 1 }
```

**Response (404 Not Found)** when the key does not exist.

### GET /local_read?key=mykey

Returns the value stored on the receiving node only. This endpoint bypasses quorum logic and leader routing and is intended for consistency testing.

---

## Replication Strategies

### Leader-Follower

| Configuration | Write Behavior | Read Behavior | Total Write Latency |
|---|---|---|---|
| W=5, R=1 | Leader sequentially updates all 4 followers before responding | Leader reads locally | approximately 1000 ms |
| W=1, R=5 | Leader responds after local write only | Leader sequentially reads from all 4 followers and returns the highest version | approximately 250 ms write and 250 ms read |
| W=3, R=3 | Leader updates 2 followers before responding | Leader reads from 2 followers and returns the highest version | approximately 600 ms |

### Leaderless

| Configuration | Write Behavior | Read Behavior |
|---|---|---|
| W=5, R=1 | Write coordinator sequentially propagates to all 4 peers | Node reads and returns local value |

### Simulated Delays

Artificial delays are applied on every node to expose inconsistency windows:

- Write path: each node sleeps for 200 ms before writing and responding.
- Read path: each node sleeps for 50 ms before responding.

---

## Project Structure

```
cs6650-assignment4/
├── kv-node/                  # Spring Boot KV service (shared by all nodes)
│   ├── src/
│   │   ├── main/java/
│   │   │   ├── controller/   # KVController (PUT /kv, GET /kv, GET /local_read)
│   │   │   ├── service/      # LeaderFollowerService, LeaderlessService
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

Nodes are configured entirely via environment variables.

### Leader-Follower

| Variable | Description | Example |
|---|---|---|
| `ROLE` | Node role | `leader` or `follower` |
| `FOLLOWER_URLS` | Comma-separated follower addresses for leader | `http://node2:8080,http://node3:8080,...` |
| `WRITE_QUORUM_SIZE` | Write quorum size W | `1`, `3`, or `5` |
| `READ_QUORUM_SIZE` | Read quorum size R | `1`, `3`, or `5` |

### Leaderless

| Variable | Description | Example |
|---|---|---|
| `PEER_URLS` | Comma-separated addresses of all other nodes | `http://node2:8080,http://node3:8080,...` |

---

## Running Locally

### Prerequisites

- Docker and Docker Compose
- Java 17
- Maven

### Start a 5-node cluster

```bash
docker compose up --build
```

This starts one leader on port `8080` and four followers on ports `8081` through `8084`.

### Example Requests

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
| `testStrongConsistencyLeader` | W=5, R=1 | Read from leader returns latest value after write |
| `testStrongConsistencyFollower` | W=5, R=1 | Read from any follower returns latest value after write |
| `testInconsistencyWindow` | W=1, R=1 | `local_read` on follower within propagation window can return stale data |
| `testLeaderlessInconsistency` | W=5, R=1 | Read from non-coordinator within propagation window can return stale data |

---

## Infrastructure

All AWS resources are defined under `terraform/`.

### Resources

- 6 EC2 instances of type `t3.micro` for 5 database nodes and 1 load tester instance
- 1 application load balancer in front of all 5 nodes for leaderless mode
- Security groups for inter-node HTTP and load tester access

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

Remember to run `terraform destroy` or stop all EC2 instances when they are not in use to avoid unnecessary AWS charges.

---

## Load Testing

The load tester is derived from Assignment 2 and supports configurable read and write ratios with stale-read detection.

### Key Design Choices

- Small key space at or below 500 keys to keep same-key reads and writes close in time
- Version tracking where the client records last written version per key and flags lower-version reads as stale
- Separate latency recording for reads and writes for distribution analysis

### Usage

```bash
cd load-tester
mvn exec:java -Dexec.args="--target http://<leader-ip>:8080 --threads 32 --duration 60 --write-ratio 0.1"
```

### Read and Write Ratios Tested

| Writes | Reads |
|---|---|
| 1% | 99% |
| 10% | 90% |
| 50% | 50% |
| 90% | 10% |

Results are exported as CSV and include per-request latency, version, staleness flag, and timestamp.

---

## Error Handling

- If a node is unreachable during replication, the system logs the error and returns `503 Service Unavailable` to the client.
- Retry logic, failure detection, and node recovery are intentionally out of scope for this experimental system.
