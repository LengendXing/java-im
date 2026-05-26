# Java IM — 全栈即时通讯系统

A full-stack instant messaging system with Vert.x server, Vue 3 web client, JavaFX desktop client, and Android (Kotlin + Compose) client. WeChat-style UI across all platforms.

## Architecture

```
┌─────────────┐   ┌─────────────┐   ┌──────────────┐   ┌──────────────┐
│  Web Client  │   │ Desktop(JavaFX)│ │ Android(Compose)│ │  TCP/WS Raw  │
│  Vue3 + TS   │   │  JavaFX 17    │ │  Kotlin + M3  │ │  Any client  │
└──────┬───────┘   └──────┬────────┘ └──────┬────────┘ └──────┬────────┘
       │ WebSocket          │ TCP              │ TCP           │ TCP/WS
       ▼                    ▼                  ▼               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        GatewayVerticle (8800/8801)                     │
│                   TCP + WebSocket + PacketCodec                         │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ EventBus
       ┌────────────┬───────────┼───────────┬────────────┐
       ▼            ▼           ▼           ▼            ▼
┌────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│AuthVerticle│ │C2CVerticle│ │GroupVert │ │SessionVert│ │PushVerticle│
│ JWT+Redis  │ │ 单聊消息   │ │群聊+好友  │ │会话+同步  │ │ 推送路由   │
└────────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
       │            │           │           │            │
       ▼            ▼           ▼           ▼            ▼
┌──────────┐  ┌──────────┐  ┌──────────────────────────────────────┐
│  Redis   │  │  MySQL   │  │           dufs (File Server)          │
│  :6379   │  │  :3306   │  │           :5100                       │
└──────────┘  └──────────┘  └──────────────────────────────────────┘
```

## Features

### Core (v0.1.x)
- User registration/login with JWT authentication
- C2C (one-to-one) text messaging
- Group text messaging
- Offline message sync
- Session list with unread counts
- Heartbeat keep-alive
- Multi-platform: TCP, WebSocket, HTTP API

### Multimedia & Social (v0.2.x)
- **File/Image upload** via dufs file server
- **Friend system**: apply / accept / reject / list / pending requests
- **Group management**: create / invite / kick / dissolve
- **Message recall** (within 2 minutes)
- **Message read/unread** status tracking
- **Voice message** support (duration in protocol)

### Optimization & Extension (v0.3.x-0.4.x)
- **Message search** (keyword search across sessions)
- **Message forward / quote** (protocol-level support)
- **@mention** in group messages
- **Prometheus /metrics** endpoint for monitoring
- **Docker deployment** with dufs file server
- **Environment variable** configuration for containers

## Quick Start

### Prerequisites

- Java 17+
- Node.js 20+ (for web client)
- Docker & Docker Compose (for containerized deployment)
- Android SDK (for Android client)

### Docker Compose (Recommended)

```bash
# Clone the repository
git clone https://github.com/LengendXing/java-im.git
cd java-im

# Start all services (Redis, MySQL, dufs, IM Server, Web Client)
docker-compose up -d

# Services:
# - IM Server:  tcp://localhost:8800, ws://localhost:8801, http://localhost:8080
# - Web Client: http://localhost:3000
# - dufs Files: http://localhost:5100
# - MySQL:      localhost:3306
# - Redis:      localhost:6379
```

### Local Development

```bash
# 1. Start infrastructure
docker-compose up -d redis mysql dufs

# 2. Build and run the server
cd server
./gradlew run

# 3. Start the web client
cd ../client-web
npm install
npm run dev

# 4. Run the desktop client
cd ../client-pc
./gradlew run

# 5. Build the Android client
cd ../client-android
./gradlew assembleDebug
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `IM_TCP_PORT` | 8800 | TCP gateway port |
| `IM_WS_PORT` | 8801 | WebSocket gateway port |
| `IM_HTTP_PORT` | 8080 | HTTP API port |
| `IM_REDIS_HOST` | 127.0.0.1 | Redis host |
| `IM_REDIS_PORT` | 6379 | Redis port |
| `IM_MYSQL_HOST` | 127.0.0.1 | MySQL host |
| `IM_MYSQL_PORT` | 3306 | MySQL port |
| `IM_MYSQL_DB` | im_db | MySQL database |
| `IM_MYSQL_USER` | im_user | MySQL user |
| `IM_MYSQL_PASSWORD` | im_pass_2024 | MySQL password |
| `IM_JWT_SECRET` | change-me-in-production | JWT signing secret |
| `DUFS_URL` | http://dufs:5100 | dufs file server URL |

## Protocol

### Binary Packet Format (22-byte header + body)

```
+--------+--------+--------+--------+----------+------------+---------+
| Magic  |Version |  Cmd   |MsgType | Sequence | BodyLength | Padding |
| 2Bytes | 2Bytes | 2Bytes | 1Byte  | 4Bytes   | 4Bytes     | 7Bytes  |
+--------+--------+--------+--------+----------+------------+---------+
|                        Protobuf Body                                 |
+----------------------------------------------------------------------+
```

- **Magic**: `0x4D49` ("MI")
- **MsgType**: `0=REQUEST`, `1=RESPONSE`, `2=NOTIFY`

### Command IDs

| Cmd | Hex | Description |
|-----|-----|-------------|
| AUTH | 0x0001 | Authentication |
| AUTH_ACK | 0x0002 | Auth response |
| HEARTBEAT | 0x0003 | Heartbeat |
| C2C_MSG | 0x0101 | C2C message |
| C2C_MSG_ACK | 0x0102 | C2C response |
| C2C_MSG_NOTIFY | 0x0103 | C2C push notify |
| GROUP_MSG | 0x0201 | Group message |
| GROUP_MSG_ACK | 0x0202 | Group response |
| GROUP_MSG_NOTIFY | 0x0203 | Group push notify |
| MSG_ACK | 0x0110 | Message delivery ACK |
| MSG_READ | 0x0111 | Mark messages read |
| MSG_RECALL | 0x0121 | Recall a message |
| SYNC | 0x0301 | Offline sync |
| SESSION_LIST | 0x0401 | Session list |
| FRIEND_APPLY | 0x0501 | Friend request |
| FRIEND_ACCEPT | 0x0511 | Accept friend |
| FRIEND_REJECT | 0x0512 | Reject friend |
| FRIEND_LIST | 0x0502 | Friend list |
| GROUP_CREATE | 0x0601 | Create group |
| GROUP_INVITE | 0x0603 | Invite to group |
| GROUP_KICK | 0x0604 | Kick from group |
| GROUP_DISSOLVE | 0x0605 | Dissolve group |
| FILE_UPLOAD | 0x0701 | File upload |

### HTTP REST API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /api/register | No | Register new user |
| POST | /api/login | No | Login, returns JWT |
| GET | /api/user/:id | Yes | Get user info |
| GET | /api/friend/list | Yes | Friend list |
| GET | /api/friend/requests | Yes | Pending friend requests |
| POST | /api/friend/apply | Yes | Send friend request |
| POST | /api/friend/accept | Yes | Accept friend request |
| POST | /api/friend/reject | Yes | Reject friend request |
| POST | /api/group/create | Yes | Create group |
| POST | /api/group/invite | Yes | Invite users to group |
| POST | /api/group/kick | Yes | Kick users from group |
| POST | /api/group/dissolve | Yes | Dissolve group (owner only) |
| GET | /api/group/:id/members | Yes | Group member list |
| POST | /api/message/recall | Yes | Recall message (2min limit) |
| POST | /api/message/read | Yes | Mark session as read |
| GET | /api/message/search | Yes | Search messages |
| POST | /api/file/upload | Yes | Upload file (multipart) |
| GET | /health | No | Health check |
| GET | /metrics | No | Prometheus metrics |

### Error Codes

| Code | Description |
|------|-------------|
| 1001 | Token expired / invalid |
| 1002 | Permission denied |
| 1003 | Invalid parameters |
| 1004 | Resource not found |

## Project Structure

```
java-im/
├── protocol/              # Protobuf protocol definitions
│   └── src/main/proto/im.proto
├── server/                # Vert.x 4.5.x server
│   ├── src/main/java/com/im/server/
│   │   ├── common/        # PacketCodec, SnowflakeId, Cmd, Config
│   │   ├── gateway/       # GatewayVerticle, HttpApiVerticle
│   │   ├── logic/         # Auth, C2C, Group, Session, Push verticles
│   │   └── storage/       # DatabaseService, RedisService, models
│   ├── src/main/resources/
│   │   ├── schema.sql
│   │   └── application.json
│   ├── src/test/          # 26 unit tests
│   └── Dockerfile
├── client-web/            # Vue 3 + TypeScript web client
│   ├── src/
│   │   ├── views/         # Login, Register, Main (tabs)
│   │   ├── components/    # Chat components
│   │   ├── stores/        # Pinia stores
│   │   └── i18n/          # zh/en translations
│   └── Dockerfile
├── client-pc/             # JavaFX 17 desktop client
│   └── src/main/java/com/im/client/
│       ├── controller/    # Login, Register, Main controllers
│       ├── model/         # JavaFX Property models
│       ├── network/       # TcpConnection, PacketCodec, ResponseHandler
│       ├── service/       # Auth, Chat, Session services
│       └── util/          # Config, JwtUtil
├── client-android/        # Kotlin + Jetpack Compose
│   └── app/src/main/java/com/im/client/
│       ├── data/
│       │   ├── local/     # Room DB (MessageEntity, SessionEntity)
│       │   ├── model/     # Domain models
│       │   ├── remote/    # TcpConnection, ApiService, PacketCodec
│       │   └── repository/  # Auth, Chat, Session repos
│       ├── service/       # ImService (foreground), NotificationHelper
│       └── ui/            # Login, Register, Sessions, Chat, Theme
├── docker-compose.yml
├── .env.example
└── plan.md
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Server | Vert.x 4.5.x, Protobuf, Redis, MySQL, JWT |
| Web Client | Vue 3, TypeScript, Tailwind CSS, Pinia, vue-i18n |
| Desktop Client | JavaFX 17, rxcontrols, OkHttp, Protobuf |
| Android Client | Kotlin, Jetpack Compose, Material3, Room, OkHttp, DataStore, Coil |
| File Server | dufs (lightweight Rust file server) |
| Deployment | Docker Compose, Nginx |

## License

MIT
