# CS6650 Assignment 4 — Team Plan

---

## 整体架构理解

### 两种数据库模型

| | Leader-Follower | Leaderless |
|---|---|---|
| 节点数 N | 5（1 Leader + 4 Follower） | 5（全对等） |
| 写入去向 | 必须发给 Leader | 任意节点（ALB 负载均衡） |
| 读取去向 | 任意节点 | 任意节点（ALB） |
| 版本号分配 | Leader 统一分配 | Write Coordinator 分配 |
| 需实现配置 | W=5/R=1、W=1/R=5、W=3/R=3 | W=5/R=1 |

### 延迟规则（强制）
- 任何节点收到 **write**：sleep **200ms** 再响应
- 任何节点收到 **read**（/kv GET 或 /local_read）：sleep **50ms** 再响应
- W=5 总写延迟 ≈ 5 × 200ms = **1s**

### 核心数据结构
```java
record KVEntry(String value, int version) {}
ConcurrentHashMap<String, KVEntry> store = new ConcurrentHashMap<>();
```

---

## 模块拆分

### 🟢 模块 0｜Load Tester 改造（最先完成）
**认领人：Yunhong Huang**

其他模块都需要 load tester 来跑数据，所以这个最先交付。

#### 任务清单
- [ ] 基于 A2 load tester 修改
- [ ] **小 key 空间**（建议 100~500 个 key），保证同 key 的读写在时间上聚集
- [ ] **可配置读写比例**：1/99、10/90、50/50、90/10
- [ ] **Stale read 检测**：
  ```java
  Map<String, Integer> lastWrittenVersion = new ConcurrentHashMap<>();
  // 写完后：lastWrittenVersion.put(key, responseVersion)
  // 读完后：if (responseVersion < lastWrittenVersion.getOrDefault(key, 0)) staleCount++
  ```
- [ ] **写入目标**：
    - Leader-Follower：写只发 Leader IP，读发任意节点（round-robin）
    - Leaderless：读写都发 ALB
- [ ] **指标收集**：
    - 每个请求的延迟（read / write 分开记录）
    - stale read 次数 & 比率
    - 同 key 读写时间间隔（用于画分布图）
- [ ] **结果输出**：CSV 或 JSON，供画图用

```java
record RequestResult(String type, String key, long latencyMs,
                     int version, boolean isStale, long timestamp) {}
```

---

### 模块 A｜核心 KV 节点 + Leader-Follower 实现
**认领人：Qingyu Cheng**

#### 任务清单
- [ ] `KVNode` 基础类：ConcurrentHashMap 存储、版本号递增
- [ ] **三个 HTTP 端点**：
    - `PUT /kv` → 写入逻辑（含 200ms 延迟）
    - `GET /kv` → 读取逻辑（含 50ms 延迟）
    - `GET /local_read` → 仅读本节点，不转发（测试专用）
- [ ] **Leader 写逻辑**（按 W 配置顺序传播）：
  ```
  收到 PUT → 分配 version+1 → 顺序发 PUT 给 W-1 个 Follower
  → 每个 Follower 睡 200ms 后 ACK → Leader 自己睡 200ms → 返回 201
  ```
- [ ] **Leader 读逻辑**（按 R 配置收集）：
  ```
  R=1: 本地读（睡 50ms）→ 返回
  R>1: 顺序请求 R-1 个 Follower → 取最高 version → 返回
  ```
- [ ] **Follower 逻辑**：接收 Leader 的 PUT/GET 转发，正常睡眠响应
- [ ] 环境变量配置：`ROLE`, `FOLLOWER_URLS`, `WRITE_QUORUM_SIZE`, `READ_QUORUM_SIZE`

#### 关键注意
- Leader 版本分配必须加锁（`AtomicInteger` per key 或 synchronized）
- 顺序传播（sequential，非并发），延迟才能叠加

---

### 模块 B｜Leaderless 实现 + 单元测试
**认领人：Runxin Shao**

#### 任务清单
- [ ] **Write Coordinator 逻辑**（W=N=5）：
  ```
  任意节点收到 PUT → 成为 Coordinator → 分配 version+1
  → 自己睡 200ms 写入 → 顺序发 PUT 给所有 Peer（每个睡 200ms）
  → 全部 ACK 后 → 返回 201
  ```
- [ ] **Read 逻辑**（R=1）：本地读，睡 50ms，返回自己的值
- [ ] 环境变量：`PEER_URLS`（其他 4 个节点地址）
- [ ] **Leader-Follower 单元测试**（3个）：
    - Test 1: W=5 强一致 → PUT leader → GET leader → 应返回新值
    - Test 2: W=5 强一致 → PUT leader → GET any follower → 应返回新值
    - Test 3: W=1 暴露不一致 → PUT leader → **立即** local_read follower → 应有 stale
- [ ] **Leaderless 单元测试**（1个）：
    - PUT 到随机节点 → update window 内 GET 其他节点 → 应有 stale
    - Coordinator ACK 后 GET Coordinator → 应一致
    - Coordinator ACK 后 GET 其他节点 → 应一致

#### 关键注意
- Test 3 的"立即"是关键：在 4×200ms 窗口内发 local_read，需要多线程同时发
- 使用 `@SpringBootTest` + `TestRestTemplate`

---

### 模块 C｜基础设施 + 报告
**认领人：Ziqi Yang**

#### 任务清单
- [ ] **Dockerfile**（同一镜像，env 区分角色）
- [ ] **docker-compose.yml**（本地 5 节点联调用）
- [ ] **Terraform**：
    - 5 个 EC2（t3.micro）作为 DB 节点
    - 1 个 EC2 作为 load tester
    - Leaderless ALB + Target Group（5 节点）
    - Security Group：节点间 HTTP 互通 + load tester → 节点
- [ ] **Spring Boot 配置**：`application.yml` + env 变量优先级
- [ ] **PDF 报告**：
    - 每种配置（3种 LF + 1种 Leaderless）各 10 分
    - 图表：Read 延迟分布、Write 延迟分布、同 key 读写时间间隔分布
    - 分析：为什么不同配置有这样的结果？哪种适合什么场景？

---

## 关键 API 设计

```
PUT /kv              Body: {"key":"k","value":"v"}   → 201 {"key":"k","version":1}
GET /kv?key=k                                         → 200 {"key":"k","value":"v","version":1} | 404
GET /local_read?key=k   (仅本节点，不转发，测试用)   → 200 {"key":"k","value":"v","version":1} | 404
PUT /kv/internal     (Leader→Follower 内部传播用)    → 200
```

---

## 开发顺序

```
阶段一（最先交付）
└── 模块 0 [Yunhong]: Load Tester 改造 ← 完全独立，最先完成

阶段二（并行）
├── 模块 A [Qingyu]: KVNode + Leader-Follower ← 先把接口和类定好推上去
└── 模块 C [Ziqi]:   基础设施 ← 不依赖业务逻辑，可与 Qingyu 并行

阶段三（等 Qingyu 稳定后）
└── 模块 B [Runxin]: 拿到 Qingyu 的代码后再写 Leaderless + 单元测试
```

> **Branch 策略**：统一在 main branch 开发，模块间路径隔离（`load-tester/`、`kv-node/`、`terraform/`），冲突风险极低。Qingyu 和 Runxin 同在 `kv-node/` 下，Qingyu 需先将 `KVEntry`、Controller、Service 接口定好并推送，Runxin 再开始；每次 push 前 `git pull --rebase`。

---

## 坑点提醒

1. **顺序 vs 并行**：Leader 必须顺序更新 Follower，W=5 总延迟才能≈1s，产生 inconsistency window
2. **版本号并发安全**：多写并发时，版本递增必须原子操作
3. **local_read 绕过路由**：直接读本节点内存，不经过任何 Leader 转发逻辑
4. **Leaderless W=N**：Coordinator 必须等所有 peer ACK 才返回 201
5. **Load tester key 空间**：key 太多→同 key 读写间隔太大→stale read 捕获不到，建议 ≤ 500