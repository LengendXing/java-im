# Java IM — 开发计划 v0.5.0（三端功能补齐）

## 当前状态：v0.4.1 已完成

### 服务端（100%完成）
- [x] 核心通讯（TCP+WebSocket+HTTP REST）
- [x] 单聊/群聊消息收发
- [x] 会话管理+未读计数
- [x] 好友系统（申请/接受/拒绝/列表/请求列表）
- [x] 群组管理（创建/邀请/踢出/解散）
- [x] 消息撤回(2分钟)/已读状态/搜索
- [x] 文件上传(dufs)/图片消息
- [x] E2EE密钥交换 + Double Ratchet(AES-256-GCM)
- [x] 离线推送(APNs/FCM)
- [x] Vert.x Cluster + Folkmq + RNacos
- [x] Prometheus + Grafana + Loki监控
- [x] 消息按月分表 + 大群读写扩散
- [x] Docker单机/集群部署配置
- [x] 37个单元测试通过

### 客户端功能对照

| 功能 | 服务端API | PC | Android | Web |
|------|-----------|:--:|:-------:|:---:|
| 登录/注册 | ✅ | ✅ | ✅ | ✅ |
| 单聊文字 | ✅ | ✅ | ✅ | ✅ |
| 群聊文字 | ✅ | ✅ | ✅ | ✅ |
| 图片/文件消息 | ✅ | ✅ | ✅ | ❌ |
| 会话列表+未读 | ✅ | ✅ | ✅ | ✅ |
| 好友请求(接受/拒绝) | ✅ | ✅ | ✅(请求) | ❌ |
| 好友列表+搜索 | ✅(需新增search) | ❌ | ❌ | ❌ |
| 好友申请 | ✅ | ❌ | ❌ | ❌ |
| 群创建 | ✅ | ✅ | ✅ | ❌ |
| 群邀请/踢/解散 | ✅ | ✅ | ❌ | ❌ |
| 群成员列表 | ✅ | ❌ | ❌ | ❌ |
| 消息撤回 | ✅ | ✅ | ✅ | ❌ |
| 消息搜索 | ✅ | ❌ | ✅ | ❌ |
| 已读标记 | ✅ | ✅ | ✅ | ❌ |
| 深色模式 | N/A | ❌ | ❌ | ✅ |
| 设置页 | N/A | ❌ | ❌ | ❌ |
| E2EE加密 | ✅ | ❌ | ❌ | ❌ |

---

## v0.5.0 开发计划

### Sprint 0 — 服务端补充（1个任务）

- [ ] 新增 `GET /api/user/search?q=xxx` 用户模糊搜索API
- [ ] DatabaseService新增 `searchUsers(keyword, limit)` 方法
- [ ] 编译验证 + 测试

### Sprint 1 — Web客户端补齐（7个任务，缺口最大，优先完成）

- [ ] 好友管理页面
  - [ ] FriendsView.vue：好友列表+搜索添加好友+好友请求列表
  - [ ] api.ts：新增friendApi.list/requests/apply/accept/reject/search
  - [ ] stores/friend.ts：Pinia store管理好友状态
- [ ] 群管理页面
  - [ ] GroupsView.vue：群列表+创建群+群管理(邀请/踢/解散/成员)
  - [ ] api.ts：新增groupApi.create/invite/kick/dissolve/members
  - [ ] stores/group.ts：Pinia store
- [ ] 文件上传+图片消息
  - [ ] api.ts：新增fileApi.upload
  - [ ] ImageMessage.vue / FileMessage.vue：消息渲染组件
  - [ ] ChatView.vue：发送按钮增加图片/文件选项
- [ ] 消息撤回
  - [ ] ChatView.vue：右键菜单增加撤回选项
  - [ ] websocket.ts：处理MSG_RECALL_NOTIFY
- [ ] 消息搜索
  - [ ] SearchView.vue：搜索页
  - [ ] api.ts：新增messageApi.search
- [ ] 设置页
  - [ ] SettingsView.vue：个人信息+深色模式开关+关于
- [ ] 编译验证(`pnpm build`)

### Sprint 2 — Android客户端补齐（4个任务）

- [ ] 通讯录Tab
  - [ ] ContactsTab.kt：好友列表+添加好友搜索
  - [ ] AddFriendScreen/ViewModel.kt：搜索用户+申请好友
  - [ ] ApiService.kt：新增searchUsers/getFriendList
  - [ ] NavGraph.kt：增加底部导航第2Tab"通讯录"
- [ ] 群管理UI
  - [ ] GroupManageScreen/ViewModel.kt：群详情页(邀请/踢/解散按钮)
  - [ ] GroupMemberScreen.kt：成员列表
  - [ ] ApiService.kt：新增getGroupMembers
- [ ] 设置页
  - [ ] SettingsScreen/ViewModel.kt：个人信息+深色模式+关于
  - [ ] NavGraph.kt：增加Settings路由
- [ ] 编译验证(`./gradlew :client-android:assembleDebug`)

### Sprint 3 — PC客户端补齐（5个任务）

- [ ] 好友列表+搜索
  - [ ] FriendService.java：新增loadFriendList/searchUsers/applyFriend方法
  - [ ] Main.fxml：增加好友列表Tab
  - [ ] MainController.java：好友列表Cell渲染+搜索+申请对话框
- [ ] 消息搜索
  - [ ] MessageService.java：新增searchMessages方法
  - [ ] Main.fxml：增加搜索区域
  - [ ] MainController.java：搜索结果展示
- [ ] 设置页
  - [ ] Main.fxml：增加Settings Tab
  - [ ] MainController.java：设置页控制器（个人信息编辑+深色模式+关于）
- [ ] 深色模式
  - [ ] dark.css：深色主题CSS文件
  - [ ] 切换逻辑：scene.getStylesheets().add/remove
- [ ] 编译验证(`./gradlew :client-pc:build`)

### Sprint 4 — E2EE三端集成（3个任务）

- [ ] Web端E2EE
  - [ ] pnpm add libsodium-wrappers
  - [ ] e2ee.ts：X25519 DH + Double Ratchet + AES-256-GCM
  - [ ] ChatView.vue：加密会话标识+加密发送/解密接收
  - [ ] SettingsView.vue：密钥管理(生成/上传/查看)
- [ ] Android端E2EE
  - [ ] implementation("com.google.crypto.tink:tink-android:1.13.0")
  - [ ] DoubleRatchetSession.kt：移植服务端逻辑
  - [ ] E2eeManager.kt：密钥生命周期管理
  - [ ] ChatScreen.kt：加密会话UI
- [ ] PC端E2EE
  - [ ] 依赖server模块的DoubleRatchetSession（同项目引用）
  - [ ] E2eeService.java：密钥管理+加密解密
  - [ ] MainController.java：加密会话UI
- [ ] 三端加密互通测试

---

## 延后至 v0.6.0

- [ ] 群聊E2EE(Sender Keys方案)
- [ ] 密钥轮换(自动signedPrekey更新)
- [ ] 消息回复/引用UI
- [ ] 语音消息录制播放
- [ ] 冷热数据分离
- [ ] OpenTelemetry分布式追踪
- [ ] iOS APNs客户端集成

---

## 关键文件索引

| 类别 | 路径 |
|------|------|
| 服务端源码 | `server/src/main/java/com/im/server/` |
| 服务端测试 | `server/src/test/java/com/im/server/` |
| E2EE核心 | `server/src/main/java/com/im/server/e2ee/DoubleRatchetSession.java` |
| 协议定义 | `protocol/src/main/proto/im.proto` |
| PC客户端 | `client-pc/src/main/java/com/im/client/` |
| PC资源 | `client-pc/src/main/resources/` |
| Android客户端 | `client-android/app/src/main/java/com/im/client/` |
| Web客户端 | `client-web/src/` |
| 单机部署 | `docker-compose.standalone.yml` |
| 集群部署 | `docker-compose.cluster.yml` |
| 环境变量 | `.env.example` |
| 开发计划 | `plan.md` |
| 变更记录 | `maintain.md` |
| PRD | `docs/PRD-v0.5.0.md` |
| 技术方案 | `docs/tech-spec-v0.5.0.md` |
| 本计划 | `docs/plan-v0.5.0.md` |
