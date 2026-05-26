# Java IM 架构设计文档

## 1. 架构总览

采用 **Gateway-Logic-Storage** 三层分离架构，各层无状态可水平扩展。

```
┌─────────────────────────────────────────────────────────┐
│                     客户端层                              │
│   PC (JavaFX)  ·  Android (Kotlin)  ·  Web (Vue3/WS)    │
└─────────────┬───────────────┬──────────────┬─────────────┘
              │ TCP+Protobuf  │ TCP+Protobuf │ WebSocket
              ▼               ▼              ▼
┌─────────────────────────────────────────────────────────┐
│                    接入层 (Gateway)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │ Gateway-1│  │ Gateway-2│  │ Gateway-N│  ← 无状态水平扩展│
│  │ Vert.x   │  │ Vert.x   │  │ Vert.x   │               │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘               │
│       │EventBus      │EventBus      │EventBus             │
└───────┼──────────────┼──────────────┼────────────────────┘
        ▼              ▼              ▼
┌─────────────────────────────────────────────────────────┐
│                    逻辑层 (Logic)                         │
│  ┌─────────────┐ ┌──────────────┐ ┌───────────────┐     │
│  │ Auth-Logic  │ │ Message-Logic│ │ Group-Logic   │     │
│  │ 登录/Token  │ │ 单聊/群聊/ACK│ │ 群组CRUD      │     │
│  └─────────────┘ └──────────────┘ └───────────────┘     │
│  ┌─────────────┐ ┌──────────────┐ ┌───────────────┐     │
│  │ Friend-Logic│ │ Session-Logic│ │ Push-Logic    │     │
│  │ 好友关系    │ │ 会话/未读     │ │ 在线/离线推送  │     │
│  └─────────────┘ └──────────────┘ └───────────────┘     │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│                   存储层 (Storage)                       │
│  ┌────────┐ ┌────────┐ ┌───────┐ ┌──────┐ ┌────────┐  │
│  │ MySQL  │ │ Redis  │ │ MinIO │ │Kafka │ │ Nacos  │  │
│  │用户/关系│ │状态/路由│ │文件   │ │削峰  │ │注册发现│  │
│  └────────┘ └────────┘ └───────┘ └──────┘ └────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 2. 各层职责

### 2.1 Gateway（接入层）

**职责：**
- 维护客户端长连接（TCP / WebSocket）
- 协议解析（Protobuf 解码/编码）
- 连接认证（Token 校验）
- 心跳保活（30s 间隔，90s 超时）
- 消息路由（根据 cmd 分发到 Logic 层）
- 连接级监控（在线数、上行/下行消息量）

**Vert.x 实现要点：**
- 使用 `NetServer` 处理 TCP 长连接
- 使用 `HttpServer` + WebSocket 处理 Web 端
- EventBus 地址格式：`im.logic.{cmd}`，如 `im.logic.C2C_MSG`
- 连接上下文通过 `Connection` 对象在 Verticle 中管理

**连接模型：**
```
客户端 ──TCP──▶ NetServer (Gateway Verticle)
                    │
                    ├─ 解码 Protobuf → Message对象
                    ├─ Token校验 → Redis 查询
                    ├─ EventBus.send("im.logic.C2C_MSG", msg)
                    │
                    ◀── EventBus.consumer("im.gateway.{userId}") ── 推送消息
                    │
                    ├─ 编码 Protobuf → ByteBuf
                    ──▶ 客户端
```

### 2.2 Logic（逻辑层）

**职责：**
- 核心业务逻辑处理
- 消息持久化（先存储后同步）
- 会话管理（创建/更新/删除）
- 未读计数维护
- 消息路由（查询接收方 Gateway 实例）
- ACK 确认处理

**Verticle 划分：**

| Verticle | EventBus 地址 | 职责 |
|----------|--------------|------|
| AuthVerticle | `im.logic.AUTH` | 登录/登出/Token校验 |
| C2CVerticle | `im.logic.C2C_MSG` | 单聊消息处理 |
| GroupVerticle | `im.logic.GROUP_MSG` | 群聊消息处理 |
| SessionVerticle | `im.logic.SESSION` | 会话/未读管理 |
| FriendVerticle | `im.logic.FRIEND` | 好友关系管理 |
| GroupMgmtVerticle | `im.logic.GROUP_MGMT` | 群组CRUD |
| PushVerticle | `im.logic.PUSH` | 消息推送调度 |

### 2.3 Storage（存储层）

**Redis 用途：**

| Key 模式 | 说明 | TTL |
|----------|------|-----|
| `im:route:{userId}` | 用户连接的 Gateway 实例 ID | 登出时删除 |
| `im:online:{userId}` | 用户在线状态 | 心跳续期 120s |
| `im:unread:{userId}:{sessionId}` | 会话未读数 | 无过期 |
| `im:unread:total:{userId}` | 全局未读总数 | 无过期 |
| `im:token:{userId}` | 登录 Token | 7 天 |
| `im:seq:{sessionId}` | 会话消息序号 INCR | 无过期 |

**MySQL 表设计（Phase 1）：**

```sql
-- 用户表
CREATE TABLE im_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    salt VARCHAR(32) NOT NULL,
    nickname VARCHAR(64),
    avatar_url VARCHAR(256),
    status TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 好友关系表
CREATE TABLE im_friend (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    status TINYINT DEFAULT 0, -- 0=申请中 1=已通过 2=已拒绝
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_friend (friend_id)
);

-- 群组表
CREATE TABLE im_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    owner_id BIGINT NOT NULL,
    announcement VARCHAR(512),
    max_members INT DEFAULT 500,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 群成员表
CREATE TABLE im_group_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role TINYINT DEFAULT 0, -- 0=普通 1=管理员 2=群主
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, user_id)
);

-- 消息表（按月分库，库内分100张表）
CREATE TABLE im_message_XX (
    id BIGINT PRIMARY KEY, -- 雪花ID
    session_id VARCHAR(64) NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT,
    group_id BIGINT,
    seq BIGINT NOT NULL,
    msg_type TINYINT NOT NULL, -- 1=文本 2=图片 3=文件 4=语音 5=系统
    content TEXT,
    extra JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_seq (session_id, seq),
    INDEX idx_sender (sender_id)
);

-- 会话表
CREATE TABLE im_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL, -- 对方userId 或 群groupId
    type TINYINT NOT NULL, -- 1=单聊 2=群聊
    last_msg_id BIGINT,
    last_msg_content VARCHAR(256),
    last_msg_time DATETIME,
    unread_count INT DEFAULT 0,
    is_top TINYINT DEFAULT 0,
    is_muted TINYINT DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_target (user_id, target_id, type)
);
```

## 3. 消息流转

### 3.1 单聊消息流

```
发送方客户端
    │ TCP: C2C_MSG(request)
    ▼
Gateway-1
    │ EventBus.send("im.logic.C2C_MSG")
    ▼
C2CVerticle
    │ 1. 生成 seq = Redis.INCR("im:seq:{sessionId}")
    │ 2. 生成 msgId = SnowflakeId.next()
    │ 3. 写入 im_message 表（先存储）
    │ 4. 更新发送方会话（im_session）
    │ 5. 更新接收方会话（写扩散）
    │ 6. 更新接收方未读 Redis.INCR
    │ 7. 查询接收方路由 Redis.GET("im:route:{receiverId}")
    ▼
PushVerticle
    │ EventBus.send("im.gateway.{receiverGatewayId}")
    ▼
Gateway-N
    │ 编码 Protobuf → 推送 C2C_MSG(notify)
    ▼
接收方客户端
    │ 收到消息 → 发送 ACK(request)
    ▼
```

### 3.2 群聊消息流

```
发送方客户端
    │ TCP: GROUP_MSG(request)
    ▼
Gateway-1
    │ EventBus.send("im.logic.GROUP_MSG")
    ▼
GroupVerticle
    │ 1. 生成 seq / msgId
    │ 2. 写入 im_message（群会话，写一次）
    │ 3. 更新群会话
    │ 4. 查询群成员列表（Redis缓存 → DB兜底）
    │ 5. 在线成员：按 Gateway 分组，合并推送
    │ 6. 离线成员：更新未读计数，异步离线推送
    ▼
PushVerticle
    │ 对每个 Gateway 实例：
    │   EventBus.send("im.gateway.{gatewayId}", 批量userId列表+消息)
    ▼
Gateway-N
    │ 遍历本机连接，推送给目标用户
    ▼
接收方客户端
```

### 3.3 离线消息同步

```
用户上线 → Gateway-1
    │ 1. AuthVerticle 校验 Token
    │ 2. 注册路由 Redis.SET("im:route:{userId}", gatewayId)
    │ 3. 标记在线 Redis.SET("im:online:{userId}", 1, TTL=120s)
    │ 4. 客户端发送 SYNC 请求（携带每个会话的 lastSeq）
    ▼
SessionVerticle
    │ 对每个会话：
    │   SELECT * FROM im_message WHERE session_id=? AND seq > lastSeq ORDER BY seq LIMIT 50
    │ → 批量返回离线消息
    │ → 客户端逐批 ACK → 继续拉取下一批
```

## 4. 消息有序性保障

**核心策略：会话内有序，全局无序**

1. **会话分区：** `sessionId = min(userId1, userId2) + "_" + max(userId1, userId2)`（单聊）或 `group_{groupId}`（群聊）
2. **局部发号：** 每个会话独立 `Redis.INCR("im:seq:{sessionId}")`，保证会话内递增
3. **路由固定：** 同一会话的消息通过一致性哈希路由到同一 Logic 实例
4. **客户端排序：** 按 seq 升序排列展示，不依赖接收时间

## 5. 消息可靠性

**三级 ACK 机制：**

| 级别 | 说明 |
|------|------|
| 客户端→Gateway | 请求携带 sequenceId，Gateway 回复 ACK(response) |
| Gateway→Logic | EventBus 请求-回复模式，Logic 处理完成后回复 |
| 客户端确认 | 收到推送后发送 ACK(notify)，服务端标记已送达 |

**离线保障：**
- 消息先存储后同步（写扩散模式）
- 离线消息持久化在 DB，上线后主动拉取
- ACK 超时（30s）则重试推送，最多3次
- 客户端本地持久化 + 服务端幂等去重（msgId + seq）

## 6. 部署拓扑（开发环境）

```
docker-compose.yml
├── im-gateway    (Vert.x, port 8800 TCP / 8801 WS)
├── im-logic      (Vert.x, EventBus集群)
├── redis         (port 6379)
├── mysql         (port 3306)
├── minio         (port 9000)
├── nacos         (port 8848)
└── prometheus    (port 9090) + grafana (port 3000)
```
