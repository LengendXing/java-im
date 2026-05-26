# Java IM v0.4.1 迭代需求文档（PRD）

> 版本：v0.4.1 | 日期：2026-05-26 | 基线版本：v0.4.0

---

## 一、背景

v0.4.0 已完成 5 个 Sprint 的核心实现，但对照 PRD v0.4.0 仍有以下未完成项需补全。

---

## 二、待实现功能清单

### 模块 C：消息分库分表补全

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| C3 | 历史数据迁移工具 | P1 | 提供 migrate 命令/工具，将 im_message 原始表数据按 serverTime 分散到 im_message_{YYYYMM} 分表，支持 dry-run 模式 |
| C5 | 冷热分离 | P2 | 3 个月以上数据归档到冷存储，热表只保留近 3 月，延后至 v0.5.0 |

---

### 模块 E：离线推送补全

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| E3 | APNs 实际 SDK 集成 | P0 | 集成 pushy 库（com.eatthepath:pushy），向 Apple APNs HTTP/2 发送推送，payload 含 sender + 消息摘要 |
| E4 | FCM 实际 SDK 集成 | P0 | 集成 firebase-admin SDK（com.google.firebase:firebase-admin），向 Google FCM 发送推送 |
| E5 | 推送频率限制完善 | P1 | 同一用户 1 分钟内最多推送 5 条，超出合并为"你有 N 条新消息"；Redis 滑动窗口计数 |
| E6 | 安卓客户端 FCM 集成 | P1 | 安卓端接入 Firebase Cloud Messaging，接收推送通知 |
| E7 | iOS 客户端 APNs 集成 | P2 | 预留，需 Mac 开发环境，延后 |

---

### 模块 F：E2EE 补全

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| F2 | Double Ratchet 会话密钥派生 | P0 | 发起对话时拉取对方 PreKey Bundle，执行 X3DH 计算共享密钥，Double Ratchet 派生消息密钥（客户端实现） |
| F3 | 消息加密/解密 | P0 | 客户端发送前 AES-256-GCM 加密，接收后解密；服务端仅转发密文（客户端实现） |
| F4 | 密钥存储 | P1 | 客户端本地加密存储密钥链（Android Keystore / Keychain / 系统密钥库） |
| F5 | 群聊加密 | P2 | Sender Keys 协议，延后至 v0.5.0 |
| F6 | 密钥轮换 | P2 | 每 100 条消息或 7 天自动轮换 Ratchet 密钥，延后至 v0.5.0 |

---

### 模块 G：可观测性补全

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| G5 | OpenTelemetry 分布式追踪 | P2 | 消息从发送到推送全链路 traceId，延后至 v0.5.0 |

---

### 模块 H：压力测试补全

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| H2 | 基准测试报告 | P0 | 1k/5k/10k 并发场景：TPS、P50/P95/P99 延迟、错误率、资源占用 |
| H3 | 72h 稳定性测试 | P1 | 72 小时持续运行，内存泄漏检测，连接池泄漏检测 |
| H4 | 瓶颈分析 | P1 | 定位瓶颈点（DB/Redis/网络/CPU），输出优化建议 |
| H5 | 自动化压测脚本 | P2 | shell 脚本一键执行压测 + 生成 HTML 报告，延后至 v0.5.0 |

---

### 模块 I：RNacos 补全

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| I2 | RNacos 集群部署 | P1 | 3 节点 Raft 集群 Docker Compose 配置 |
| I4 | 服务发现替代 Redis 路由 | P0 | PushVerticle 从 RNacos 拉取 Gateway 实例列表，替代 Redis 路由表查找 |
| I6 | RNacos 配置中心 | P1 | ServerConfig 改为从 RNacos Config 拉取，支持热更新（dataId: im-server.yml） |

---

### 协议变更

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| P1 | Cmd 0x0801 KEY_BUNDLE_REQUEST | P0 | 拉取对方 PreKey Bundle |
| P2 | Cmd 0x0802 KEY_BUNDLE_RESPONSE | P0 | 返回 PreKey Bundle |
| P3 | Cmd 0x0803 PUSH_TOKEN_REGISTER | P0 | 注册推送 Token |
| P4 | MessageContent 新增 encrypted_key (field 14) | P0 | AES 密钥密文 |
| P5 | MessageContent 新增 encrypted_content (field 15) | P0 | 消息密文 |
| P6 | MessageContent 新增 is_encrypted (field 16, bool) | P0 | 是否加密消息 |
| P7 | SessionInfo 新增 diffusion_mode (field 12) | P1 | 群扩散模式 |

---

### 交付物补全

| 编号 | 功能 | 优先级 | 描述 |
|------|------|--------|------|
| D1 | 数据迁移工具 | P1 | C3 的实现产出 |
| D2 | README 集群部署指南 | P1 | 更新 README.md 含集群部署指南 |

---

## 三、实施优先级

### 第一批：服务端核心补全（P0）

1. 协议扩展：Cmd 0x0801~0x0803 + E2EE Protobuf 字段
2. E3：APNs pushy SDK 集成
3. E4：FCM firebase-admin SDK 集成
4. I4：服务发现替代 Redis 路由

### 第二批：服务端功能补全（P1）

5. E5：推送频率限制完善（Redis 滑动窗口）
6. C3：历史数据迁移工具
7. I6：RNacos 配置中心热更新
8. I2：RNacos 集群部署
9. D2：README 集群部署指南

### 第三批：客户端实现

10. E6：安卓客户端 FCM 集成
11. F2-F4：E2EE 客户端（Double Ratchet + AES-256-GCM + 密钥存储）

### 第四批：压测验证

12. H2：基准测试报告
13. H4：瓶颈分析

### 延后至 v0.5.0

- C5：冷热分离
- F5：群聊加密（Sender Keys）
- F6：密钥轮换
- G5：OpenTelemetry 分布式追踪
- H5：自动化压测脚本
- E7：iOS APNs 集成

---

## 四、验收标准

- 所有 P0 功能可编译运行，APNs/FCM 配置后实际可推送
- PushVerticle 可从 RNacos 发现 Gateway 实例
- 协议新增 Cmd 正确编解码
- 迁移工具可 dry-run + 实际迁移
- 安卓端可收到 FCM 推送通知
- E2EE 客户端可加密/解密单聊消息
- 压测结果达到 PRD v0.4.0 目标指标
