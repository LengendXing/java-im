## v0.1.0 - 2026-05-25

### 变更内容
- 初始化项目：PRD/架构设计/技术方案三份文档
- Gradle 多模块项目骨架（protocol + server）
- Protobuf 协议定义（20+ 消息类型，覆盖认证/心跳/单聊/群聊/同步/会话/好友/群组）
- 服务端核心 Verticle：GatewayVerticle / AuthVerticle / C2CVerticle / GroupVerticle / SessionVerticle / PushVerticle
- HTTP REST API：注册/登录/健康检查
- TCP 长连接接入（GatewayVerticle, port 8800）
- WebSocket 接入（port 8801）
- Docker Compose 部署方案（Redis + MySQL + MinIO + Server）

### 影响范围
- 全新项目，无历史影响

### 功能列表
- 用户注册/登录（JWT Token 认证）
- 单聊文字消息收发
- 群聊文字消息收发
- 离线消息同步
- 会话列表 + 未读计数
- 心跳保活
- 多端 WebSocket 支持

## v0.1.1 - 2026-05-26

### 变更内容
- 服务端关键问题修复：DB schema 自动初始化、LAST_INSERT_ID 竞态消除、SessionVerticle Redis 异步合并修复
- PushVerticle 正确路由：每个 Gateway 生成唯一 ID，Redis 路由表指向具体 Gateway 实例
- HTTP API 鉴权：除 login/register 外所有接口需 Bearer Token
- CORS 支持：Web 客户端跨域访问
- 用户上线/下线路由自动注册/注销
- schema.sql typo 修复（TINYNT → TINYINT）
- 添加 .gitignore、.env.example
- 26 个单元测试通过（PacketCodec、SnowflakeId、RequestEnvelope、AuthVerticle、PushEnvelope、ConnectionManager）
- Web 客户端 WeChat 风格 UI 重构：绿白主色调、底部 Tab 导航、会话列表气泡样式、消息气泡+头像布局、通讯录页面、个人中心页面、i18n 中英双语、light/dark 主题切换
- 安装 anydb-mcp（MySQL+Redis MCP）

### 影响范围
- 服务端 GatewayVerticle/AuthVerticle/PushVerticle/SessionVerticle/DatabaseService/RedisService/HttpApiVerticle
- Web 客户端全部组件重构

### 功能列表
- 用户注册/登录（JWT Token + HTTP 鉴权）
- 单聊文字消息收发（含乐观更新）
- 群聊文字消息收发
- 离线消息同步
- 会话列表 + 未读计数（Redis + DB 合并）
- 心跳保活 + 在线路由自动续期
- 多端 WebSocket 支持（CORS）
- WeChat 风格 Web UI
## v0.1.2 - 2026-05-26

### 变更内容
- 桌面客户端 WeChat 风格 UI 重构：绿色主色调(#07C160)、白底界面、绿色气泡(#95EC69)
- 桌面客户端集成 rxcontrols 库、登录/注册页添加 WeChat 绿色 "W" Logo
- 移除重复的 ImConnection.java（保留 TcpConnection.java 作为唯一连接实现）
- MessageModel.sentByMe 改为 BooleanProperty（与其他 JavaFX Property 字段一致）
- App.stop() 改为优雅断开 TCP 连接而非 System.exit(0)
- 安卓客户端 WeChat 绿色主题重构：Color.kt/Theme.kt 使用 #07C160 主色调
- 安卓客户端 TcpConnection readLoop 从 O(n²) mutableListOf<Byte> 重写为高效 readFully 直读
- 安卓客户端 LoginScreen 添加 WeChat "W" Logo、ChatScreen 使用 #95EC69 绿色气泡

### 影响范围
- 桌面端：style.css、LoginController、RegisterController、MainController、login.fxml、register.fxml、MessageModel、App
- 安卓端：Color.kt、Theme.kt、TcpConnection、LoginScreen、ChatScreen

### 功能列表
- 桌面端 WeChat 风格完整 UI（登录/注册/聊天/会话列表）
- 安卓端 WeChat 风格完整 UI（登录/注册/会话/聊天）
- 安卓端前台服务（ImService）+ 消息通知（NotificationHelper）
- 安卓端 Room 本地存储（MessageEntity + SessionEntity）
- 安卓端 DataStore 持久化 JWT Token
- 安卓端协程化 TCP 连接 + 自动重连 + 心跳
