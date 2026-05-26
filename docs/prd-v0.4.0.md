# Java IM v0.4.0 迭代需求文档（PRD）

> 版本：v0.4.0 | 日期：2026-05-26 | 基线版本：v0.3.0

---

## 一、背景与目标

### 1.1 现状

v0.3.0 已完成单机版全部核心功能（注册/登录、单聊/群聊文字图片文件消息、好友系统、群组管理、消息撤回/已读/搜索、Docker 部署、Prometheus 指标），但存在以下瓶颈：

| 问题 | 影响 |
|------|------|
| 单机 Gateway 无法横向扩展 | 单点故障，连接数上限受限于单机内存 |
| EventBus 进程内通信 | 多实例部署时消息无法跨节点投递 |
| 消息单表无分片 | 数据量 >500 万行后查询性能劣化 |
| 无日志聚合 | 多容器环境下排障困难 |
| 无可观测性面板 | /metrics 有数据但无可视化 |
| 无离线推送 | App 杀后台后收不到消息通知 |
| 大群(>500人)写入扩散 | 群消息写入放大 N 倍，延迟显著 |
| 无端到端加密 | 消息明文存储，合规风险 |

### 1.2 迭代目标

v0.4.0 定位为 **"生产就绪"** 版本，核心目标：

1. **可横向扩展**：Gateway/Logic 支持多实例部署，无状态化
2. **可观测**：Grafana 面板 + ELK 日志聚合，5 分钟内定位问题
3. **高性能**：消息分库分表 + 大群读写扩散优化，单群支持 5000 人
4. **可靠触达**：APNs/FCM 离线推送，消息触达率 > 99%
5. **安全合规**：端到端加密，消息落盘密文

---

## 二、功能模块

### 模块 A：Vert.x Cluster 集群化

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| A1 | Hazelcast Cluster Manager | P0 | 引入 vertx-hazelcast 替换默认 ClusterManager，支持多 Vert.x 实例 EventBus 跨节点通信 |
| A2 | Gateway 无状态化 | P0 | GatewayVerticle 启动时生成唯一 gatewayId（UUID），注册到 Redis 路由表；连接信息存 Redis，不存本地 Map |
| A3 | Logic Verticle 多实例 | P0 | AuthVerticle/C2CVerticle/GroupVerticle/SessionVerticle 可部署多实例，通过 Hazelcast EventBus 广播消息 |
| A4 | 优雅上下线 | P1 | Gateway 上线注册路由、下线注销路由；Logic 上线后拉取最新路由表；SIGTERM 信号触发 drain + unregister |
| A5 | 集群配置文件 | P1 | hazelcast.xml 配置文件，支持静态 IP 列表和 Kubernetes DNS 发现两种模式 |

**验收标准：**
- 2 个 Gateway 实例 + 2 个 Logic 实例，客户端连接任一 Gateway 均可收发消息
- 滚动重启一个 Gateway，连接自动迁移到另一个，消息零丢失

---

### 模块 B：Kafka 消息中间件

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| B1 | Kafka Producer 集成 | P0 | Logic Verticle 将 C2C/Group 消息写入 Kafka topic（im-c2c、im-group），替代直接 EventBus |
| B2 | Kafka Consumer 集成 | P0 | PushVerticle 消费 Kafka 消息，按 gatewayId 路由推送 |
| B3 | 消息持久化保证 | P0 | Producer acks=all，Consumer 手动 commit offset，确保消息至少投递一次 |
| B4 | 顺序性保证 | P1 | 单聊消息按 session_id 分区，保证同一会话消息有序 |
| B5 | 死信队列 | P2 | 推送失败超过 3 次的消息进入 DLQ topic，人工/自动重试 |

**验收标准：**
- 下游 PushVerticle 宕机 30s 后恢复，期间消息不丢（Kafka 持久化）
- 单聊消息严格有序（同一 session_id 的消息 seq 递增）

---

### 模块 C：消息分库分表

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| C1 | 按月分表策略 | P0 | im_message 按月分表：im_message_202605、im_message_202606…，路由规则 session_id % 12 → 12 库 × 按月分表 |
| C2 | 分表路由层 | P0 | DatabaseService 增加分表路由逻辑，SQL 自动路由到目标库/表，上层代码无感知 |
| C3 | 历史数据迁移 | P1 | 提供 migrate 工具，将 im_message 原始表数据按月分散到分表 |
| C4 | 跨表查询适配 | P1 | 消息搜索（searchMessages）适配跨月查询，合并排序返回 |
| C5 | 冷热分离 | P2 | 3 个月以上数据归档到冷存储（MySQL 归档实例 / S3），热表只保留近 3 月 |

**分片规则：**
```
库序号 = hash(session_id) % 12     → im_db_0 ~ im_db_11
表名   = im_message_{YYYYMM}       → 按月滚动
```

**验收标准：**
- 单表 500 万行数据，分表后查询延迟 < 50ms（P99）
- 跨月搜索返回结果正确且有序

---

### 模块 D：大群读写扩散优化

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| D1 | 群成员数阈值判定 | P0 | 群成员 ≤ 200 人：写入扩散（为每个成员写一条消息）；> 200 人：读扩散（只写一条，拉取时按需读取） |
| D2 | 读扩散消息拉取 | P0 | 新增 GroupPullVerticle，大群成员上线/打开群聊时，拉取最近 N 条消息 + 未读计数 |
| D3 | 大群未读计数优化 | P0 | 大群未读计数改为 Redis Bitmap（每位代表一个 seq），替代逐条 +1 |
| D4 | 群成员缓存 | P1 | 群成员列表 Redis 缓存，变更时失效；减少 DB 查询 |
| D5 | 扩散模式动态切换 | P2 | 群人数跨过阈值时自动切换扩散模式，历史消息兼容读取 |

**验收标准：**
- 2000 人群发送一条消息，写入扩散需 < 500ms；读扩散需 < 100ms（写入）+ 拉取 < 200ms
- max_members 上限提升至 5000

---

### 模块 E：离线推送（APNs / FCM）

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| E1 | 推送 Token 管理 | P0 | 客户端注册/更新 APNs/FCM Token，存入 im_push_token 表（user_id, platform, token, updated_at） |
| E2 | 离线判定 | P0 | PushVerticle 推送时检查 Redis 在线路由，目标不在线则调用推送服务 |
| E3 | APNs 推送 | P0 | 集成 pushy 库，向 Apple APNs HTTP/2 发送推送，payload 含 sender + 消息摘要 |
| E4 | FCM 推送 | P0 | 集成 firebase-admin SDK，向 Google FCM 发送推送 |
| E5 | 推送频率限制 | P1 | 同一用户 1 分钟内最多推送 5 条，超出合并为"你有 N 条新消息" |
| E6 | 安卓客户端 FCM 集成 | P1 | 安卓端接入 Firebase Cloud Messaging，接收推送通知 |
| E7 | iOS 客户端 APNs 集成 | P2 | （预留，需 Mac 开发环境） |

**数据库变更：**
```sql
CREATE TABLE IF NOT EXISTS im_push_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    platform TINYINT NOT NULL COMMENT '1=iOS 2=Android 3=Web',
    token VARCHAR(256) NOT NULL,
    bundle_id VARCHAR(128) DEFAULT '',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_platform (user_id, platform),
    INDEX idx_token (token(64))
) ENGINE=InnoDB;
```

**验收标准：**
- Android 客户端杀后台 30s 后发送消息，5s 内收到 FCM 通知
- 同一用户 1 分钟内收到 10 条消息，实际推送 ≤ 5 条，第 6 条显示"你有 5 条新消息"

---

### 模块 F：端到端加密（E2EE）

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| F1 | 密钥交换协议 | P0 | 基于 X3DH（Extended Triple Diffie-Hellman），用户注册时生成 Identity Key + Signed PreKey + One-Time PreKeys，上传公钥到服务器 |
| F2 | 会话密钥派生 | P0 | 发起对话时拉取对方 PreKey Bundle，执行 X3DH 计算共享密钥，Double Ratchet 派生消息密钥 |
| F3 | 消息加密/解密 | P0 | 客户端发送前 AES-256-GCM 加密，接收后解密；服务端仅转发密文 |
| F4 | 密钥存储 | P1 | 客户端本地加密存储密钥链（Android Keystore / Keychain / 系统密钥库） |
| F5 | 群聊加密 | P2 | Sender Keys 协议，群成员共享对称密钥，发件人加密一次，收件人各自解密 |
| F6 | 密钥轮换 | P2 | 每 100 条消息或 7 天自动轮换 Ratchet 密钥 |

**数据库变更：**
```sql
CREATE TABLE IF NOT EXISTS im_user_key (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    key_type TINYINT NOT NULL COMMENT '1=IdentityKey 2=SignedPreKey 3=OneTimePreKey',
    key_id INT NOT NULL,
    public_key BLOB NOT NULL,
    signature BLOB,
    used TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_key_type_id (user_id, key_type, key_id)
) ENGINE=InnoDB;
```

**验收标准：**
- 中间人（服务端管理员）无法解密消息内容
- 消息落盘（MySQL）为密文，即使 DB 泄露也无法读取
- 密钥轮换后历史消息仍可解密

---

### 模块 G：可观测性（Grafana + ELK）

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| G1 | Grafana 面板 | P0 | 导入 /metrics 数据，面板包含：连接数/消息量/延迟/P99/错误率/Redis 命中率/DB 慢查询 |
| G2 | Prometheus 配置 | P0 | prometheus.yml 添加 im-server target，15s 采集间隔 |
| G3 | Loki 日志聚合 | P0 | Vert.x Logger 输出 JSON 格式日志，Promtail 采集，Loki 存储，Grafana 查询 |
| G4 | 告警规则 | P1 | Prometheus AlertManager 规则：连接数 > 10k、消息延迟 P99 > 1s、错误率 > 1% |
| G5 | 分布式追踪 | P2 | OpenTelemetry 集成，消息从发送到推送全链路 traceId |

**Docker Compose 扩展：**
```yaml
# 新增服务
prometheus:
  image: prom/prometheus:v2.51.0
  volumes: [./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml]

grafana:
  image: grafana/grafana:10.4.0
  ports: ["3000:3000"]

loki:
  image: grafana/loki:2.9.0

promtail:
  image: grafana/promtail:2.9.0
  volumes: [./monitoring/promtail.yml:/etc/promtail/config.yml]
```

**验收标准：**
- Grafana 面板 5s 内刷新，可查看最近 1h/6h/24h 趋势
- Loki 可按 userId/sessionId/level 检索日志，响应 < 2s
- AlertManager 触发告警后 30s 内通知到飞书 Webhook

---

### 模块 H：压力测试

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| H1 | JMeter 测试脚本 | P0 | 覆盖：注册/登录/单聊/群聊/心跳/文件上传，参数化可调并发数 |
| H2 | 基准测试报告 | P0 | 1k/5k/10k 并发场景：TPS、P50/P95/P99 延迟、错误率、资源占用 |
| H3 | 稳定性测试 | P1 | 72 小时持续运行，内存泄漏检测，连接池泄漏检测 |
| H4 | 瓶颈分析 | P1 | 定位瓶颈点（DB/Redis/网络/CPU），输出优化建议 |
| H5 | 自动化压测脚本 | P2 | shell 脚本一键执行压测 + 生成 HTML 报告 |

**目标指标：**

| 场景 | 并发数 | TPS | P99 延迟 | 错误率 |
|------|--------|-----|----------|--------|
| 单聊文字消息 | 5,000 | ≥ 3,000 | ≤ 200ms | < 0.1% |
| 群聊文字消息(200人群) | 1,000 | ≥ 500 | ≤ 500ms | < 0.1% |
| 登录 | 1,000 | ≥ 500 | ≤ 300ms | < 0.1% |
| 文件上传(1MB) | 500 | ≥ 100 | ≤ 2s | < 0.5% |

**验收标准：**
- 所有场景达到目标指标
- 72h 稳定性测试内存增长 < 100MB/天，无 OOM

---

### 模块 I：Nacos 服务注册发现

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| I1 | Nacos Server 部署 | P1 | Docker Compose 新增 nacos 服务，standalone 模式 |
| I2 | 服务注册 | P1 | Gateway/Logic 启动时注册到 Nacos，携带 host/port/weight/metadata |
| I3 | 服务发现 | P1 | PushVerticle 从 Nacos 拉取 Gateway 实例列表，替代 Redis 路由表 |
| I4 | 心跳保活 | P1 | Nacos 客户端自动心跳，实例下线自动摘除 |
| I5 | 配置中心 | P2 | ServerConfig 改为从 Nacos Config 拉取，支持热更新 |

---

## 三、非功能性需求

| 类别 | 指标 |
|------|------|
| 可用性 | 99.9%（全年停机 < 8.76h），单节点故障不影响服务 |
| 数据安全 | 消息 E2EE 加密，DB 泄露不可读；符合 GDPR 数据删除要求 |
| 可扩展性 | 水平扩容无需停机，新实例加入集群自动发现 |
| 兼容性 | v0.4.0 客户端兼容 v0.3.0 服务端（协议向后兼容） |
| 日志保留 | 热日志 30 天，归档日志 180 天 |
| 部署 | Docker Compose 一键拉起全部服务（含 Kafka/Nacos/Grafana/Loki） |

---

## 四、数据库变更汇总

| 变更 | 类型 | 说明 |
|------|------|------|
| im_push_token | 新建表 | APNs/FCM Token 存储 |
| im_user_key | 新建表 | E2EE 公钥存储 |
| im_message 按月分表 | 结构变更 | im_message → im_message_{YYYYMM}，12 库 |
| im_group.max_members | 配置变更 | 默认 500 → 5000 |
| im_group.diffusion_mode | 新增列 | TINYINT DEFAULT 0（0=写扩散 1=读扩散） |

---

## 五、协议变更

| 变更 | 说明 |
|------|------|
| 新增 Cmd 0x0801 KEY_BUNDLE_REQUEST | 拉取对方 PreKey Bundle |
| 新增 Cmd 0x0802 KEY_BUNDLE_RESPONSE | 返回 PreKey Bundle |
| 新增 Cmd 0x0803 PUSH_TOKEN_REGISTER | 注册推送 Token |
| MessageContent 新增 encrypted_key (field 14) | AES 密钥密文 |
| MessageContent 新增 encrypted_content (field 15) | 消息密文 |
| MessageContent 新增 is_encrypted (field 16, bool) | 是否加密消息 |
| SessionInfo 新增 diffusion_mode (field 12) | 群扩散模式 |

---

## 六、迭代计划

### Sprint 1（v0.4.0-alpha.1）— 集群化 + Kafka

| 任务 | 预计工时 | 依赖 |
|------|----------|------|
| A1: Hazelcast Cluster Manager | 4h | - |
| A2: Gateway 无状态化 | 4h | A1 |
| A3: Logic Verticle 多实例 | 2h | A1 |
| B1-B3: Kafka Producer/Consumer | 8h | A1 |
| B4: 消息有序性 | 4h | B1 |
| Docker Compose 扩展（Kafka/Zookeeper） | 2h | B1 |
| **小计** | **24h** | |

### Sprint 2（v0.4.0-alpha.2）— 分库分表 + 大群优化

| 任务 | 预计工时 | 依赖 |
|------|----------|------|
| C1-C2: 按月分表 + 路由层 | 8h | - |
| C3: 历史数据迁移工具 | 4h | C1 |
| D1-D2: 大群读写扩散 | 8h | - |
| D3: 大群未读 Bitmap | 4h | D1 |
| D4: 群成员缓存 | 2h | D1 |
| **小计** | **26h** | |

### Sprint 3（v0.4.0-alpha.3）— 离线推送 + E2EE

| 任务 | 预计工时 | 依赖 |
|------|----------|------|
| E1-E2: 推送 Token + 离线判定 | 4h | - |
| E3: APNs 推送 | 6h | E1 |
| E4: FCM 推送 | 4h | E1 |
| E5: 推送频率限制 | 2h | E3/E4 |
| E6: 安卓 FCM 集成 | 4h | E4 |
| F1-F3: X3DH + Double Ratchet + AES | 16h | - |
| F4: 密钥存储 | 4h | F1 |
| **小计** | **40h** | |

### Sprint 4（v0.4.0-beta.1）— 可观测性 + 压测

| 任务 | 预计工时 | 依赖 |
|------|----------|------|
| G1-G2: Grafana + Prometheus | 4h | - |
| G3: Loki 日志聚合 | 4h | - |
| G4: 告警规则 | 2h | G1 |
| H1: JMeter 脚本 | 6h | Sprint 1-3 |
| H2: 基准测试报告 | 4h | H1 |
| H3: 72h 稳定性测试 | 4h | H1 |
| **小计** | **24h** | |

### Sprint 5（v0.4.0-rc.1）— Nacos + 集成验收

| 任务 | 预计工时 | 依赖 |
|------|----------|------|
| I1-I4: Nacos 服务注册发现 | 6h | Sprint 1 |
| I5: Nacos 配置中心 | 4h | I1 |
| 全链路集成测试 | 8h | Sprint 1-4 |
| 文档更新（README/maintain/plan） | 2h | - |
| **小计** | **20h** | |

**总预计工时：134h**

---

## 七、风险与依赖

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| Kafka 运维复杂度高 | 中 | 高 | 提供单机模式（无 Kafka）兼容部署，Kafka 为可选依赖 |
| E2EE 客户端实现复杂 | 高 | 中 | Sprint 3 优先实现单聊加密，群聊加密延至 v0.5.0 |
| 分库分表数据迁移风险 | 中 | 高 | 迁移工具支持 dry-run 模式，双写验证后切读 |
| APNs 证书需 Apple 开发者账号 | 高 | 低 | 先实现 FCM，APNs 预留接口，后续补充 |
| JMeter 压测环境资源不足 | 低 | 中 | 使用 Docker 限制资源模拟真实场景 |

---

## 八、交付物清单

- [ ] 源码：server / protocol / client-web / client-pc / client-android
- [ ] Docker Compose：一键部署含 Kafka/Nacos/Grafana/Loki/Prometheus
- [ ] Grafana Dashboard JSON 导出文件
- [ ] JMeter 测试脚本 + 基准测试报告
- [ ] 数据迁移工具（分库分表）
- [ ] hazelcast.xml / prometheus.yml / promtail.yml 配置文件
- [ ] README.md 更新（集群部署指南）
- [ ] maintain.md / plan.md 更新
- [ ] .env.example 更新（Kafka/Nacos/APNs/FCM 配置项）
