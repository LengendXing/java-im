# Java IM 技术方案文档

## 1. 协议设计

### 1.1 传输协议

**定长包头 + 变长包体**，采用 Protobuf v3 序列化：

```
┌──────────────────────────────────────────┐
│               包头 (22 bytes)             │
├──────┬──────┬─────┬────────┬─────────────┤
│magic │version│ cmd │msgType │ sequenceId  │
│ 2B   │ 2B    │ 2B  │ 1B     │ 4B          │
├──────┴──────┴─────┴────────┼─────────────┤
│       dataLength (4B)      │  padding 7B │  ← 预留对齐
├───────────────────────────┼─────────────┤
│          data (Protobuf encoded body)    │
└──────────────────────────────────────────┘
```

| 字段 | 长度 | 说明 |
|------|------|------|
| magic | 2B | 魔数 0xIM01，粘包/半包标识 |
| version | 2B | 协议版本，当前 0x0001 |
| cmd | 2B | 命令字（见下表） |
| msgType | 1B | 0=request 1=response 2=notify |
| sequenceId | 4B | 请求序列号，请求-响应配对 |
| dataLength | 4B | 包体长度 |
| padding | 7B | 预留对齐至 22B |

### 1.2 命令字定义

| cmd | 值 | 方向 | 说明 |
|-----|-----|------|------|
| AUTH | 0x0001 | C→S | 登录认证 |
| AUTH_ACK | 0x0002 | S→C | 认证结果 |
| HEARTBEAT | 0x0003 | C→S | 心跳请求 |
| HEARTBEAT_ACK | 0x0004 | S→C | 心跳响应 |
| C2C_MSG | 0x0101 | C→S | 单聊消息发送 |
| C2C_MSG_ACK | 0x0102 | S→C | 发送确认 |
| C2C_MSG_NOTIFY | 0x0103 | S→C | 单聊消息推送 |
| GROUP_MSG | 0x0201 | C→S | 群聊消息发送 |
| GROUP_MSG_ACK | 0x0202 | S→C | 发送确认 |
| GROUP_MSG_NOTIFY | 0x0203 | S→C | 群聊消息推送 |
| MSG_ACK | 0x0110 | C→S | 消息接收确认 |
| SYNC | 0x0301 | C→S | 离线消息同步 |
| SYNC_ACK | 0x0302 | S→C | 同步响应 |
| SESSION_LIST | 0x0401 | C→S | 会话列表请求 |
| SESSION_LIST_ACK | 0x0402 | S→C | 会话列表响应 |
| FRIEND_APPLY | 0x0501 | C→S | 好友申请 |
| FRIEND_LIST | 0x0502 | C→S | 好友列表请求 |
| GROUP_CREATE | 0x0601 | C→S | 创建群组 |
| GROUP_MEMBER_LIST | 0x0602 | C→S | 群成员列表 |
| KICKOFF | 0x0F01 | S→C | 被踢下线通知 |

### 1.3 Protobuf 消息体定义

```protobuf
syntax = "proto3";
package im.protocol;

// ===== 基础类型 =====

message UserInfo {
  int64 user_id = 1;
  string username = 2;
  string nickname = 3;
  string avatar_url = 4;
}

message MessageContent {
  int32 msg_type = 1;       // 1=文本 2=图片 3=文件 4=语音 5=系统
  string text = 2;          // 文本内容
  string url = 3;           // 媒体URL
  string file_name = 4;     // 文件名
  int64 file_size = 5;      // 文件大小
  int32 width = 6;          // 图片宽
  int32 height = 7;         // 图片高
  int32 duration = 8;       // 语音时长(秒)
  string extra = 9;         // JSON扩展字段
}

// ===== 认证 =====

message AuthRequest {
  string token = 1;
  string device_id = 2;
  int32 platform = 3;       // 1=PC 2=Android 3=Web
}

message AuthResponse {
  int32 code = 1;           // 0=成功
  string msg = 2;
  UserInfo user_info = 3;
  int64 server_time = 4;
}

// ===== 心跳 =====

message HeartbeatRequest {}

message HeartbeatResponse {
  int64 server_time = 1;
}

// ===== 单聊 =====

message C2CMsgRequest {
  int64 receiver_id = 1;
  MessageContent content = 2;
  string client_msg_id = 3; // 客户端消息ID，幂等去重
}

message C2CMsgResponse {
  int32 code = 1;
  string msg = 2;
  int64 msg_id = 3;         // 服务端消息ID
  int64 seq = 4;            // 会话序号
  int64 server_time = 5;
}

message C2CMsgNotify {
  int64 msg_id = 1;
  int64 sender_id = 2;
  string session_id = 3;
  int64 seq = 4;
  MessageContent content = 5;
  int64 server_time = 6;
}

// ===== 群聊 =====

message GroupMsgRequest {
  int64 group_id = 1;
  MessageContent content = 2;
  string client_msg_id = 3;
  repeated int64 at_user_ids = 4; // @成员列表，0=@所有人
}

message GroupMsgResponse {
  int32 code = 1;
  string msg = 2;
  int64 msg_id = 3;
  int64 seq = 4;
  int64 server_time = 5;
}

message GroupMsgNotify {
  int64 msg_id = 1;
  int64 sender_id = 2;
  int64 group_id = 3;
  string session_id = 4;
  int64 seq = 5;
  MessageContent content = 6;
  repeated int64 at_user_ids = 7;
  int64 server_time = 8;
}

// ===== 消息确认 =====

message MsgAckRequest {
  int64 msg_id = 1;
  string session_id = 2;
  int64 seq = 3;
}

// ===== 离线同步 =====

message SyncRequest {
  repeated SyncPoint points = 1; // 每个会话的同步位点
  int32 limit = 2;              // 每个会话最多拉取条数
}

message SyncPoint {
  string session_id = 1;
  int64 last_seq = 2;           // 客户端已有的最大seq
}

message SyncResponse {
  repeated SessionMessages sessions = 1;
}

message SessionMessages {
  string session_id = 1;
  repeated Message messages = 2;
  bool has_more = 3;
}

message Message {
  int64 msg_id = 1;
  int64 sender_id = 2;
  int64 seq = 3;
  MessageContent content = 4;
  int64 server_time = 5;
}

// ===== 会话 =====

message SessionListRequest {
  int64 last_update_time = 1; // 分页：上次拉取的最新时间
  int32 limit = 2;
}

message SessionListResponse {
  repeated SessionInfo sessions = 1;
}

message SessionInfo {
  string session_id = 1;
  int32 type = 2;             // 1=单聊 2=群聊
  int64 target_id = 3;        // 对方userId 或 groupId
  string name = 4;            // 对方昵称 或 群名
  string avatar_url = 5;
  string last_msg = 6;        // 最后一条消息摘要
  int64 last_msg_time = 7;
  int32 unread_count = 8;
  bool is_top = 9;
  bool is_muted = 10;
}

// ===== 好友 =====

message FriendApplyRequest {
  int64 target_user_id = 1;
  string message = 2;         // 申请留言
}

message FriendListRequest {
  int64 last_user_id = 1;     // 分页游标
  int32 limit = 2;
}

message FriendListResponse {
  repeated UserInfo friends = 1;
}

// ===== 群组 =====

message GroupCreateRequest {
  string name = 1;
  repeated int64 member_ids = 2;
}

message GroupMemberListRequest {
  int64 group_id = 1;
  int64 last_user_id = 2;
  int32 limit = 3;
}

message GroupMemberListResponse {
  repeated GroupMember members = 1;
}

message GroupMember {
  UserInfo user_info = 1;
  int32 role = 2;             // 0=普通 1=管理员 2=群主
  string joined_at = 3;
}
```

## 2. 安全层设计

### 2.1 传输安全

- TCP 连接：TLS 1.3（生产环境），开发环境可跳过
- WebSocket：WSS（TLS）
- 数据中心内部：EventBus 通信，无需额外加密

### 2.2 认证机制

```
注册 → POST /api/register → 密码 SHA-256 + 随机salt 存储
登录 → POST /api/login → 校验密码 → 签发 JWT Token（HS256, TTL=7d）
长连接 → AUTH 命令携带 Token → Gateway 校验 → Redis 查询/存储
```

**JWT Payload：**
```json
{
  "userId": 10001,
  "platform": 2,
  "deviceId": "android-xxxx",
  "iat": 1717000000,
  "exp": 1717604800
}
```

### 2.3 多端登录策略

| 策略 | 说明 |
|------|------|
| 同平台互踢 | 同一平台（如两台Android）只保留最新连接 |
| 跨平台共存 | PC + Android + Web 可同时在线 |
| 消息同步 | 所有在线端均收到推送，通过 seq 保证一致性 |

## 3. ID 生成方案

### 3.1 雪花算法 (SnowflakeId)

```
┌──────────────────────────────────────────────────┐
│ 1bit  │    41bit timestamp    │ 10bit worker │ 12bit seq │
│  0    │  millis since epoch   │  datacenter+worker │ 自增序号   │
└──────────────────────────────────────────────────┘
```

- 41bit 时间戳：可用约 69 年
- 10bit workerId：支持 1024 个实例
- 12bit 序列号：每毫秒 4096 个 ID
- 单机每秒可生成 400万+ ID

### 3.2 会话 ID 生成规则

- 单聊：`c2c_{min(userId1,userId2)}_{max(userId1,userId2)}`
- 群聊：`group_{groupId}`

## 4. 消息存储方案

### 4.1 写扩散模型（Phase 1）

```
发送方发消息 → 服务端写入:
  1. im_message 表（全局消息，按 session_id + seq 索引）
  2. 发送方 im_session（更新 last_msg + last_msg_time）
  3. 接收方 im_session（更新 last_msg + unread_count++）
  4. Redis 未读计数 INCR
```

### 4.2 存储分层

| 热度 | 存储 | 数据 | TTL |
|------|------|------|-----|
| 热 | Redis | 最近50条消息缓存、未读数、路由 | 7天 |
| 温 | MySQL | 3个月内消息、关系数据 | 按月分库 |
| 冷 | TiDB/HBase（v0.3+） | 历史消息归档 | 永久 |

## 5. 服务端模块设计

### 5.1 Vert.x Verticle 部署

```
im-server (Main Verticle)
├── GatewayVerticle (instances: N, 按CPU核心数)
│   ├── NetServer (TCP:8800)
│   └── HttpServer (WS:8801)
├── AuthVerticle (instances: 2)
├── C2CVerticle (instances: 4)
├── GroupVerticle (instances: 4)
├── SessionVerticle (instances: 2)
├── FriendVerticle (instances: 2)
├── GroupMgmtVerticle (instances: 2)
├── PushVerticle (instances: 4)
├── HttpApiVerticle (REST API:8080)
└── ClusterManager (Nacos/ZooKeeper)
```

### 5.2 EventBus 地址规范

| 地址 | 发布者 | 订阅者 | 模式 |
|------|--------|--------|------|
| `im.logic.AUTH` | Gateway | AuthVerticle | point-to-point |
| `im.logic.C2C_MSG` | Gateway | C2CVerticle | point-to-point |
| `im.logic.GROUP_MSG` | Gateway | GroupVerticle | point-to-point |
| `im.logic.SESSION` | Gateway/Logic | SessionVerticle | point-to-point |
| `im.logic.PUSH` | Logic | PushVerticle | point-to-point |
| `im.gateway.{gatewayId}` | PushVerticle | Gateway | point-to-point |
| `im.broadcast.online` | AuthVerticle | All Logic | publish |

## 6. 客户端技术方案

### 6.1 Android 客户端

- **语言：** Kotlin
- **UI：** Jetpack Compose + MVVM
- **网络：** OkIO + Protobuf TCP 长连接
- **本地存储：** Room (SQLite) 持久化消息/会话
- **推送：** Firebase Cloud Messaging (FCM)

### 6.2 Web 客户端

- **框架：** Vue 3 + TypeScript
- **UI：** 自定义组件（参考 CLAUDE.md Web 规范）
- **通信：** WebSocket + Protobuf.js / JSON fallback
- **状态：** Pinia
- **本地存储：** IndexedDB

### 6.3 PC 客户端

- **语言：** Java 17+
- **UI：** JavaFX (OpenJFX)
- **通信：** Netty TCP + Protobuf
- **本地存储：** SQLite (JDBC)
- **打包：** jpackage (安装包分发)

## 7. 开发环境搭建

### 7.1 项目结构（Gradle 多模块）

```
java-im/
├── build.gradle.kts          (根构建文件)
├── settings.gradle.kts       (模块注册)
├── docs/                     (文档)
│   ├── PRD.md
│   ├── architecture.md
│   └── tech-spec.md
├── protocol/                 (Protobuf 定义 + 生成代码)
│   ├── build.gradle.kts
│   └── src/main/proto/im.proto
├── server/                   (Vert.x 服务端)
│   ├── build.gradle.kts
│   └── src/main/java/com/im/server/
├── client-android/           (Android 客户端)
├── client-web/               (Web 客户端)
├── client-pc/                (PC 客户端)
├── docker-compose.yml
└── README.md
```

### 7.2 依赖版本

| 依赖 | 版本 | 用途 |
|------|------|------|
| Vert.x | 4.5.x | 核心框架 |
| Protobuf | 3.25.x | 序列化 |
| Netty | 4.1.x (Vert.x内嵌) | 网络层 |
| Redis (Lettuce) | 6.3.x | Redis客户端 |
| MySQL (HikariCP) | 5.1.x | 连接池 |
| JWT (jjwt) | 0.12.x | Token签发 |
| Logback | 1.4.x | 日志 |
| JUnit 5 | 5.10.x | 测试 |
