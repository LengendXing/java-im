## v0.5.0 - 2026-05-27

### 变更内容
- v0.5.0 三端功能补齐，达到生产可用
- Sprint 0：服务端新增 `GET /api/user/search?q=xxx` 用户模糊搜索API，DatabaseService新增searchUsers方法
- Sprint 1：Web客户端补齐 — searchUsers API调用、按用户名搜索添加好友、MSG_RECALL_NOTIFY处理、Cmd常量补全、消息搜索UI、昵称编辑、关于版本信息
- Sprint 2：Android客户端补齐 — ApiService新增searchUsers/getGroupMembers/applyFriend、ContactsTab（好友列表+搜索添加）、GroupManageScreen（群管理/踢人/解散）、SettingsScreen（个人信息/深色模式/关于）、NavGraph新增路由
- Sprint 3：PC客户端补齐 — FriendService新增loadFriendList/searchUsers/applyFriend、MessageService新增searchMessages、Friends/Search/Settings Tab、dark.css深色模式+切换逻辑、JwtUtil新增nickname/username存储
- Sprint 4：E2EE三端集成 — PC端E2eeService(复用server模块DoubleRatchetSession)、Web端e2ee.ts(Web Crypto API ECDH+AES-256-GCM)、Android端DoubleRatchetSession.kt+E2eeManager.kt、client-pc新增server模块依赖
- 补充提交：单设备踢出（同用户新连接踢旧连接）、C2CVerticle移除错误信息泄露、nginx WebSocket超时配置、E2EE核心实现+Benchmark+11个单元测试、docker-compose cluster/standalone、v0.5.0三份文档
- TypeScript编译修复：e2ee.ts移除未使用import、ChatPage.vue合并重复watch/useI18n import、ContactsPage.vue移除未使用addFriendLegacy函数

### 影响范围
- 服务端：HttpApiVerticle、DatabaseService
- Web客户端：api.ts、websocket.ts、constants.ts、chat.ts、ContactsPage、ChatPage、ProfilePage、e2ee.ts
- Android客户端：ApiService、ContactsTab、GroupManageScreen、SettingsScreen、NavGraph、Routes、DoubleRatchetSession.kt、E2eeManager.kt
- PC客户端：FriendService、MessageService、MainController、main.fxml、dark.css、JwtUtil、AuthService、E2eeService、build.gradle.kts

### 功能列表
- 用户模糊搜索（服务端+三端）
- 好友搜索添加（三端）
- 群管理UI（Android群管理页、PC群管理Tab）
- 消息搜索UI（Web搜索面板、PC搜索Tab）
- 设置页（Android个人信息/深色模式/关于、PC Settings Tab）
- 深色模式（PC dark.css + 切换逻辑）
- E2EE端到端加密集成（PC/Web/Android）
- 昵称编辑（Web/PC）

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

## v0.2.0 - 2026-05-26

### 变更内容
- Protobuf 协议扩展：新增 FriendAccept/Reject/RequestList、GroupInvite/Kick/Dissolve、MsgRead/Recall/RecallNotify、FileUpload、MsgSearch、quote/forward 字段
- 服务端新增：好友接受/拒绝/请求列表、群组邀请/踢出/解散、消息撤回(2分钟)、消息已读状态、消息搜索、文件上传(dufs)
- DatabaseService 新增方法：acceptFriendRequest、rejectFriendRequest、getPendingFriendRequests、removeGroupMember、dissolveGroup、isGroupOwner、recallMessage、getMessageById、updateLastReadSeq、searchMessages
- 数据库 schema 新增：im_session.last_read_seq、im_message.is_recalled
- ServerConfig 支持环境变量配置（Docker 部署）
- HttpApiVerticle 新增所有 Phase2 API 端点 + /metrics Prometheus 端点
- Docker 部署：dufs 替代 MinIO、server Dockerfile、web Dockerfile + Nginx 反向代理、healthcheck
- 客户端三端同步更新 Phase2 功能

### 影响范围
- 全模块：protocol、server、client-web、client-pc、client-android

### 功能列表
- 用户注册/登录（JWT Token + HTTP 鉴权）
- 单聊文字/图片/文件/语音消息收发
- 群聊文字/图片/文件/语音消息收发
- 离线消息同步
- 会话列表 + 未读计数 + 已读状态
- 心跳保活 + 在线路由自动续期
- 多端 WebSocket 支持（CORS）
- 好友系统（申请/接受/拒绝/列表/请求列表）
- 群组管理（创建/邀请/踢出/解散）
- 消息撤回（2分钟内）
- 文件上传下载（dufs）
- 消息搜索
- 消息引用/转发（协议支持）
- Prometheus /metrics 监控
- Docker Compose 一键部署（含 dufs）
- WeChat 风格多端 UI（#07C160 绿色主色调）

## v0.4.0-sprint1 - 2026-05-26

### 变更内容
- Vert.x Cluster 集群化：引入 vertx-hazelcast ClusterManager，支持多实例 EventBus 跨节点通信
- Gateway 无状态化：GatewayVerticle stop() 时 drain 连接 + 注销路由
- Logic Verticle 多实例：Auth/C2C/Group/Session/Push 全部支持多实例部署（默认 x2）
- Folkmq 消息中间件集成：C2C/Group 消息通过 Folkmq 持久化投递，PushVerticle 订阅 Folkmq topic
- 消息有序性：单聊按 session_id 作 sharding key，Folkmq sequence 保证同会话消息有序
- 优雅上下线：SIGTERM 信号触发 Vertx.close() + Folkmq.disconnect()
- hazelcast.xml 集群配置：支持 multicast / 静态 IP / K8s DNS 发现
- Docker Compose 新增 folkmq 服务（noearorg/folkmq-broker:1.7.13）
- ServerConfig 新增 cluster / folkmq 配置项
- Folkmq 连接失败自动降级到 EventBus 直接投递

### 功能列表
- Vert.x Cluster 集群部署（Hazelcast 多节点 EventBus 通信）
- Gateway 无状态化 + 优雅上下线
- Folkmq 持久化消息投递（im-c2c / im-group topic）
- 消息有序性（session_id sharding key）

## v0.4.0-sprint2 - 2026-05-26

### 变更内容
- 消息按月分表：im_message → im_message_{YYYYMM}，insertMessage/getMessages/searchMessages/recallMessage/getMessageById 全部自动路由
- ShardingService 分表路由层：按 serverTime 计算月表名，跨月查询合并排序，启动时自动创建当月表
- 大群读写扩散：群成员 >200 人切换读扩散（只写一条+bitmap），≤200 人写扩散（为每成员推送）
- 大群未读 Bitmap：Redis SETBIT 记录 seq 位，替代逐条 incr
- 群成员 Redis 缓存：getGroupMembers 优先 Redis，miss 时查 DB 并回填，默认 TTL 300s
- GroupPullVerticle：大群消息拉取接口（EventBus im.logic.GROUP_PULL）
- im_group 表新增 max_members(5000) + diffusion_mode(0=写扩散/1=读扩散) 字段
- DatabaseService 新增 getGroupMemberCount/getGroupDiffusionMode/updateGroupDiffusionMode 方法

### 功能列表
- 消息按月分表 + 自动路由
- 大群读写扩散混合模式（阈值200人）
- 大群未读 Redis Bitmap
- 群成员 Redis 缓存 - 2026-05-26

### 变更内容
- Docker Compose 部署方案完善：dufs 替代 MinIO、server/web Dockerfile 多阶段构建、nginx 反向代理、healthcheck
- ServerConfig 全面支持环境变量配置（Docker 友好）
- HttpApiVerticle 新增 Prometheus /metrics 端点
- 桌面客户端 Phase2 全功能：文件上传(FileUploadService)、好友请求(FriendService)、群组管理(GroupService)、消息撤回/已读(MessageService)、图片/文件消息内联渲染
- 桌面客户端 main.fxml 添加 TabPane(会话+请求)、群管理按钮、上传按钮
- Web 客户端 Phase2 全功能：文件/图片上传、好友请求接受/拒绝、群组邀请/踢出/解散、消息撤回、消息搜索、图片预览覆盖层
- 安卓客户端 Phase2 全功能：文件/图片上传、好友请求(FriendRequestsScreen)、群组创建(GroupCreateScreen)、消息撤回、搜索、Tab导航(会话+请求)
- 安卓客户端 ApiService 添加 11 个 HTTP API 方法、ChatRepository/SessionRepository 扩展、Room DB v2

### 影响范围
- 全模块：protocol、server、client-web、client-pc、client-android

### 功能列表
- 用户注册/登录（JWT Token + HTTP 鉴权）
- 单聊文字/图片/文件/语音消息收发
- 群聊文字/图片/文件/语音消息收发
- 离线消息同步
- 会话列表 + 未读计数 + 已读状态
- 心跳保活 + 在线路由自动续期
- 多端 WebSocket 支持（CORS）
- 好友系统（申请/接受/拒绝/列表/请求列表）
- 群组管理（创建/邀请/踢出/解散）
- 消息撤回（2分钟内）
- 文件上传下载（dufs）
- 消息搜索
- 消息引用/转发（协议支持）
- @成员功能（协议支持）
- 语音消息（协议支持）
- Prometheus /metrics 监控
- Docker Compose 一键部署（含 dufs + nginx）
- WeChat 风格多端 UI（#07C160 绿色主色调）

## v0.4.0-sprint3 - 2026-05-26

### 变更内容
- Push Token 管理：im_push_token 表 + POST /api/push/token
- 离线推送：PushVerticle 检测离线调用 APNs/FCM
- APNs/FCM 集成（占位，需配置激活）
- 推送频率限制（PushRateLimiter）
- E2EE 密钥交换：im_user_key 表 + API
- ServerConfig 新增 APNs/FCM 环境变量配置

### 功能列表
- 离线推送（APNs/FCM）
- 推送频率限制
- E2EE 公钥存储与交换

## v0.4.0-sprint4 - 2026-05-26

### 变更内容
- Prometheus 采集配置（15s间隔，im-server target）
- Grafana 面板 JSON（连接数/消息量/延迟/P99/错误率/Redis命中率）
- Loki 日志聚合 + Promtail 采集配置
- AlertManager 告警规则（连接数>10k、延迟P99>1s、错误率>1%）
- JMeter 压测脚本（注册/登录，参数化并发数）
- Docker Compose 新增 prometheus/grafana/loki/promtail 服务

### 功能列表
- Prometheus + Grafana 可观测性
- Loki 日志聚合
- 告警规则
- JMeter 压测脚本

## v0.4.0-sprint5 - 2026-05-26

### 变更内容
- RNacos 单机部署（Docker Compose，端口8848/10848）
- NacosRegistryService：服务注册/发现/注销（nacos-client 2.3.2）
- 启动时注册 im-server 实例，关闭时注销
- ServerConfig 新增 IM_NACOS_ENABLED / IM_NACOS_SERVER_ADDR 环境变量
- Docker Compose 新增 rnacos 服务

### 功能列表
- RNacos 服务注册发现（默认关闭，环境变量激活）
- 管理控制台端口10848

## v0.4.0-subitems - 2026-05-26

### 变更内容
- A4: GatewayVerticle.stop() 从 Nacos 注销服务实例
- B6: 推送死信告警 — PushVerticle 检测推送失败（限流/无token/SDK错误），通过飞书 Webhook 发送橙色告警卡片
- D5: 动态扩散模式切换 — 群成员数跨越200阈值时自动切换读写扩散模式并清除成员缓存
- 群成员变更时（invite/kick）自动失效 Redis 缓存
- 新增 IM_FEISHU_WEBHOOK 环境变量

### 功能列表
- Gateway 优雅下线 Nacos 注销
- 推送死信飞书告警
- 动态扩散模式双向切换 + 缓存一致性

## v0.4.1 - 2026-05-26

### 变更内容
- E2eeVerticle: 处理 KEY_BUNDLE_REQUEST（拉取对方 PreKey Bundle）和 PUSH_TOKEN_REGISTER（注册推送 Token）
- ApnsService: 集成 pushy 0.15.4 实际 SDK（APNs HTTP/2 推送）
- FcmService: 集成 firebase-admin 9.4.0 实际 SDK（FCM 推送）
- PushRateLimiter: Redis ZSET 滑动窗口限流（每分钟5条）
- PushVerticle: Nacos 服务发现替代 Redis 路由表查找 Gateway 实例
- Nacos 注册 im-gateway 服务（TCP端口），支持跨节点推送路由
- GatewayVerticle: 新增 KEY_BUNDLE_REQUEST / PUSH_TOKEN_REGISTER 命令路由
- Main: 部署 E2eeVerticle x2 实例
- 协议扩展：MessageContent 新增 encrypted_key/encrypted_content/is_encrypted；SessionInfo 新增 diffusion_mode；新增 KeyBundle/OneTimePreKey/PushToken 消息类型

### 功能列表
- E2EE 密钥交换（X3DH PreKey Bundle 拉取）
- 推送 Token 注册（APNs/FCM/Web）
- APNs 实际推送（pushy SDK）
- FCM 实际推送（firebase-admin SDK）
- 推送频率限制（Redis 滑动窗口）
- Nacos 服务发现替代 Redis 路由

## v0.4.1-p1 - 2026-05-26

### 变更内容
- MigrationTool: 历史数据迁移工具，按 serverTime 迁移 im_message 到 im_message_{YYYYMM} 分表，支持 dry-run
- NacosConfigService: RNacos 配置中心集成（dataId: im-server.yml），监听配置变更热更新
- docker-compose: RNacos 3 节点 Raft 集群部署（node1:8848, node2:8849, node3:8850）
- README: 新增集群部署指南（架构图 + Nacos 集群 + 数据迁移说明）
- .env.example: 新增 Nacos 集群配置说明
- 安卓端 FCM 集成: FcmService（FirebaseMessagingService）+ AndroidManifest 注册

### 功能列表
- 历史数据迁移（dry-run + 实际迁移）
- Nacos 配置中心热更新
- Nacos 3 节点 Raft 集群
- 集群部署指南文档
- 安卓 FCM 推送通知

## v0.4.1-p2 - 2026-05-26

### 变更内容
- DoubleRatchetSession 5项关键BUG修复：
  1. DH ratchet密钥对顺序：decrypt()中先用旧dhPair做接收链推导，再生成新dhPair做发送链推导（原代码两次DH输入相同导致输出相同）
  2. Skipped key查找：decrypt()新增skippedKeys遍历查找，支持乱序消息解密
  3. AES-GCM参数：IvParameterSpec→GCMParameterSpec（Java 17要求）
  4. prevChainLength：新增prevChainLength字段（类似Signal pn），跨DH epoch乱序消息支持
  5. PublicKey比较：equals()→Arrays.equals(encoded)，避免X509编码差异
- DoubleRatchetSessionTest 11个单元测试全部通过（含跨DH epoch乱序、64KB大包、MAX_SKIP边界等）
- DoubleRatchetBenchmark 基准测试：
  - X25519密钥生成: 3229 ops/sec (310µs/op)
  - X3DH初始化: 230 ops/sec (4.35ms/op)
  - 加密(256B): 27656 ops/sec (36µs/op)
  - 解密(256B): 46796 ops/sec (21µs/op)
  - DH ratchet交替: 612 round-trips/sec
  - 加密吞吐(64KB): 1169 MB/sec
- 瓶颈分析：X25519密钥生成(310µs)是DH ratchet步骤的主要瓶颈，X3DH初始化(4.35ms)含5次DH+1次HKDF，单链加密/解密性能充足（>27k ops/sec）

### 影响范围
- server/e2ee/DoubleRatchetSession.java（核心修复）
- server/e2ee/DoubleRatchetBenchmark.java（新增）
- server/test/e2ee/DoubleRatchetSessionTest.java（新增）

### 功能列表
- E2EE Double Ratchet 完整实现（X3DH + Double Ratchet + AES-256-GCM）
- 乱序消息解密（同链+跨DH epoch）
- 基准测试报告
