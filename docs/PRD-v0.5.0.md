# Java IM — PRD v0.5.0（三端功能补齐）

## 1. 项目概述

- **项目名**：java-im
- **定位**：企业内部IM通讯系统，支持单聊/群聊/文件/E2EE
- **目标规模**：100人以内小团队
- **当前版本**：v0.4.1
- **本次目标**：v0.5.0 三端功能全部对齐，达到生产可用

## 2. 三端技术栈

| 端 | 技术 | 网络层 | 本地存储 |
|----|------|--------|---------|
| Web | Vue 3 + Vite + TS | WebSocket + HTTP API | localStorage |
| PC | Java 17 + JavaFX 17 | TCP长连接 + HTTP API | 内存 |
| Android | Kotlin + Compose + Room | TCP长连接 + HTTP API | Room DB |

## 3. 服务端已有HTTP API

```
POST /api/register              注册 {username, password}
POST /api/login                  登录 {username, password} → {token, userId}
GET  /api/user/:id               用户信息 → {userId, username, nickname, avatarUrl}
POST /api/friend/apply           好友申请 {targetUserId, message}
POST /api/friend/accept          好友接受 {fromUserId}
POST /api/friend/reject          好友拒绝 {fromUserId}
GET  /api/friend/list            好友列表 → [{userId, username, nickname, avatarUrl}]
GET  /api/friend/requests        好友请求列表 → [{fromUserId, username, message}]
POST /api/group/create           创建群组 {name, memberIds[]} → {groupId}
POST /api/group/invite           群邀请 {groupId, userIds[]}
POST /api/group/kick             群踢人 {groupId, userId}
POST /api/group/dissolve         解散群 {groupId}
GET  /api/group/:id/members      群成员列表 → [{userId, role}]
POST /api/message/recall         消息撤回 {msgId, sessionId}
POST /api/message/read           已读标记 {sessionId, seq}
GET  /api/message/search         消息搜索 ?keyword=xxx&sessionId=xxx&limit=20
POST /api/file/upload            文件上传(multipart) → {url, fileName, fileSize}
POST /api/push/token             推送Token {platform, token, bundleId}
POST /api/e2ee/keys              E2EE公钥上传 {keyType, keyId, publicKey, signature}
GET  /api/e2ee/keys/:userId      E2EE公钥拉取 → {identityKey, signedPrekey, signature, otpk}
GET  /health                     健康检查
GET  /metrics                    Prometheus指标
```

**需新增API：**
```
GET  /api/user/search?q=xxx      用户搜索（模糊匹配username/nickname）
```

## 4. TCP协议命令（三端共用，包头22字节）

| Cmd | Hex | 方向 | 说明 |
|-----|-----|------|------|
| AUTH | 0x0001 | C→S | 认证请求 |
| AUTH_ACK | 0x0002 | S→C | 认证响应 |
| HEARTBEAT | 0x0003 | C→S | 心跳 |
| HEARTBEAT_ACK | 0x0004 | S→C | 心跳响应 |
| C2C_MSG | 0x0101 | C→S | 单聊消息 |
| C2C_MSG_ACK | 0x0102 | S→C | 发送确认 |
| C2C_MSG_NOTIFY | 0x0103 | S→C | 新消息推送 |
| GROUP_MSG | 0x0201 | C→S | 群聊消息 |
| GROUP_MSG_ACK | 0x0202 | S→C | 发送确认 |
| GROUP_MSG_NOTIFY | 0x0203 | S→C | 新消息推送 |
| MSG_ACK | 0x0110 | C→S | 消息送达确认 |
| MSG_READ | 0x0111 | C→S | 已读上报 |
| MSG_READ_NOTIFY | 0x0112 | S→C | 已读通知 |
| MSG_RECALL | 0x0121 | C→S | 消息撤回 |
| MSG_RECALL_NOTIFY | 0x0122 | S→C | 撤回通知 |
| SYNC | 0x0301 | C→S | 离线同步 |
| SYNC_ACK | 0x0302 | S→C | 同步响应 |
| SESSION_LIST | 0x0401 | C→S | 会话列表请求 |
| SESSION_LIST_ACK | 0x0402 | S→C | 会话列表响应 |
| FRIEND_APPLY | 0x0501 | C→S | 好友申请 |
| FRIEND_LIST | 0x0502 | S→C | 好友列表推送 |
| FRIEND_APPLY_NOTIFY | 0x0503 | S→C | 好友申请通知 |
| FRIEND_REQUEST_LIST | 0x0504 | S→C | 请求列表推送 |
| FRIEND_ACCEPT | 0x0511 | C→S | 好友接受 |
| FRIEND_REJECT | 0x0512 | C→S | 好友拒绝 |
| GROUP_CREATE | 0x0601 | C→S | 创建群 |
| GROUP_MEMBER_LIST | 0x0602 | S→C | 群成员列表 |
| GROUP_INVITE | 0x0603 | C→S | 群邀请 |
| GROUP_KICK | 0x0604 | C→S | 群踢人 |
| GROUP_DISSOLVE | 0x0605 | C→S | 解散群 |
| FILE_UPLOAD | 0x0701 | C→S | 文件上传通知 |
| KEY_BUNDLE_REQUEST | 0x0801 | C→S | E2EE密钥请求 |
| KEY_BUNDLE_RESPONSE | 0x0802 | S→C | E2EE密钥响应 |
| PUSH_TOKEN_REGISTER | 0x0803 | C→S | 推送Token注册 |
| PUSH_TOKEN_REGISTER_ACK | 0x0804 | S→C | 注册确认 |
| KICKOFF | 0x0F01 | S→C | 被踢下线 |

## 5. 三端缺失功能清单

### 5.1 PC客户端（JavaFX）

**已有功能：** 登录/注册、单聊/群聊文字图片文件、会话列表+未读、好友请求接受拒绝、群创建邀请踢解散、消息撤回、已读标记、文件上传(dufs)、WeChat绿色主题

**缺失功能：**
- 好友列表Tab + 用户搜索
- 消息搜索
- 设置页（个人信息编辑、深色模式、关于）
- 深色模式CSS切换
- E2EE端到端加密集成

**代码位置：** `client-pc/src/main/java/com/im/client/`
- controller/ → LoginController, MainController, RegisterController
- service/ → AuthService, ChatService, FileUploadService, FriendService, GroupService, MessageService, SessionService
- network/ → TcpConnection, PacketCodec, ResponseHandler, ImPacket
- model/ → MessageModel, SessionModel, UserModel

### 5.2 Android客户端（Jetpack Compose）

**已有功能：** 登录/注册、单聊/群聊、会话列表、好友请求列表、群创建、消息撤回/已读/搜索、文件上传、FCM推送、Room DB本地缓存、WeChat绿色主题

**缺失功能：**
- 通讯录Tab（好友列表 + 添加好友搜索）
- 群管理UI（邀请/踢/解散 + 成员列表）
- 设置页（个人信息、深色模式、关于）
- E2EE端到端加密集成

**代码位置：** `client-android/app/src/main/java/com/im/client/`
- data/remote/ → ApiService, TcpConnection, PacketCodec, ImPacket, Cmd
- data/repository/ → AuthRepository, ChatRepository, FriendRepository, GroupRepository, SessionRepository
- data/local/ → AppDatabase, Dao, Models
- ui/ → login/, register/, sessions/, chat/, contacts/, group/ (各含Screen+ViewModel)
- service/ → ImService, FcmService, NotificationHelper

### 5.3 Web客户端（Vue 3）

**已有功能：** 登录/注册、单聊/群聊文字、会话列表+Tab导航、i18n中英双语、light/dark主题切换

**缺失功能：**
- 好友管理（列表/申请/接受/拒绝/搜索）
- 群管理（创建/邀请/踢/解散/成员列表）
- 文件上传 + 图片/文件消息渲染
- 消息撤回
- 消息搜索
- 设置页
- E2EE端到端加密集成

**代码位置：** `client-web/src/`
- views/ → LoginView, RegisterView, MainView, ChatView
- views/tabs/ → Tab组件
- services/ → api.ts, websocket.ts
- stores/, components/, composables/, i18n/, proto/

## 6. E2EE Double Ratchet 接口（服务端已实现并测试）

```java
// 密钥对生成
KeyPair kp = DoubleRatchetSession.generateKeyPair();  // X25519

// 签名验签（Ed25519，需单独密钥对）
byte[] sig = DoubleRatchetSession.sign(ed25519Priv, data);
boolean ok = DoubleRatchetSession.verify(ed25519Pub, data, sig);

// 发送方初始化（X3DH）
session.initAsSender(identityKey, ephemeralKey, remoteIdentity, remoteSignedPrekey, remoteOtpkBytes);

// 接收方初始化（X3DH）
session.initAsReceiver(identityKey, signedPrekey, otpkPriv, remoteIdentity, remoteEphemeral);

// 加密
EncryptResult enc = session.encrypt(plaintext);
// enc.ciphertext, enc.iv, enc.dhPubEncoded, enc.msgNum, enc.prevChainLength

// 解密
byte[] plain = session.decrypt(ciphertext, iv, dhPubEncoded, msgNum, prevChainLength);
```

**E2EE密钥存储：** `im_user_key`表，key_type: 1=identityKey, 2=signedPrekey, 3=otpk
**协议字段：** MessageContent新增 `encrypted_key`(bytes), `encrypted_content`(bytes), `is_encrypted`(bool)
