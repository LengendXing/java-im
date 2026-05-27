# Java IM — 技术方案 v0.5.0（三端功能补齐）

## 1. 服务端新增：用户搜索API

**文件：** `server/src/main/java/com/im/server/gateway/HttpApiVerticle.java`

```java
router.get("/api/user/search").handler(ctx -> {
    // 需Bearer Token鉴权
    String q = ctx.request().getParam("q");
    if (q == null || q.trim().isEmpty()) { ctx.json(...); return; }
    dbService.searchUsers(q.trim(), 20)
        .onSuccess(users -> ctx.json(JsonArray.of(users.stream().map(...))))
        .onFailure(err -> ctx.json(errorResponse(1, "search failed")));
});
```

**DatabaseService新增方法：**
```java
public Future<List<User>> searchUsers(String keyword, int limit) {
    pool.preparedQuery("SELECT user_id, username, nickname, avatar_url FROM im_users WHERE username LIKE ? OR nickname LIKE ? LIMIT ?")
        .execute(Tuple.of("%"+keyword+"%", "%"+keyword+"%", limit))
        ...
}
```

## 2. Web客户端补齐方案（Vue 3 + Vite + TS）

### 2.1 新增文件结构

```
src/
├── views/
│   ├── ChatView.vue          (已有，需增强：图片/文件/撤回)
│   ├── LoginView.vue         (已有)
│   ├── MainView.vue          (已有，需增加Tab)
│   ├── RegisterView.vue      (已有)
│   ├── FriendsView.vue       (新增：好友列表+搜索+请求)
│   ├── GroupsView.vue        (新增：群列表+创建+管理)
│   ├── SearchView.vue        (新增：消息搜索)
│   ├── SettingsView.vue      (新增：设置页)
│   └── tabs/
│       ├── ContactsTab.vue   (新增：通讯录Tab)
│       └── DiscoverTab.vue   (新增：发现Tab)
├── services/
│   ├── api.ts                (已有，需增加好友/群/搜索API)
│   ├── websocket.ts          (已有)
│   └── e2ee.ts               (新增：E2EE加密服务)
├── components/
│   ├── FriendItem.vue        (新增)
│   ├── GroupItem.vue         (新增)
│   ├── FileMessage.vue       (新增：文件消息渲染)
│   ├── ImageMessage.vue      (新增：图片消息渲染)
│   └── SearchBar.vue         (新增)
├── stores/
│   ├── friend.ts             (新增)
│   ├── group.ts              (新增)
│   └── settings.ts           (新增)
```

### 2.2 好友管理实现

**api.ts新增：**
```typescript
export const friendApi = {
  list: () => fetch('/api/friend/list', { headers: authHeaders() }).then(r => r.json()),
  requests: () => fetch('/api/friend/requests', { headers: authHeaders() }).then(r => r.json()),
  apply: (targetUserId: number, message: string) =>
    fetch('/api/friend/apply', { method: 'POST', headers: authHeaders(), body: JSON.stringify({ targetUserId, message }) }),
  accept: (fromUserId: number) =>
    fetch('/api/friend/accept', { method: 'POST', headers: authHeaders(), body: JSON.stringify({ fromUserId }) }),
  reject: (fromUserId: number) =>
    fetch('/api/friend/reject', { method: 'POST', headers: authHeaders(), body: JSON.stringify({ fromUserId }) }),
  search: (q: string) =>
    fetch(`/api/user/search?q=${encodeURIComponent(q)}`, { headers: authHeaders() }).then(r => r.json()),
}
```

### 2.3 文件上传实现

```typescript
export const fileApi = {
  upload: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const resp = await fetch('/api/file/upload', {
      method: 'POST', headers: { Authorization: `Bearer ${getToken()}` }, body: formData
    });
    return resp.json(); // { url, fileName, fileSize }
  }
}
```

发送图片/文件消息：content_type=2(图片)/3(文件)，content_url=上传返回url

### 2.4 E2EE集成（Web端）

使用Web Crypto API实现X25519 DH + AES-256-GCM，或引入libsodium-wrappers：

```typescript
// e2ee.ts
import sodium from 'libsodium-wrappers';

export class E2eeSession {
  private session: any; // Double Ratchet state

  async initAsSender(identityKey, ephemeralKey, remoteBundle) { ... }
  async initAsReceiver(identityKey, signedPrekey, otpk, remoteEphemeral) { ... }
  async encrypt(plaintext: Uint8Array): Promise<EncryptResult> { ... }
  async decrypt(ciphertext, iv, dhPub, msgNum, prevChainLength): Promise<Uint8Array> { ... }
}
```

依赖：`pnpm add libsodium-wrappers`

## 3. Android客户端补齐方案（Kotlin + Compose）

### 3.1 新增文件

```
app/src/main/java/com/im/client/
├── ui/
│   ├── contacts/
│   │   ├── ContactsTab.kt          (新增：通讯录Tab)
│   │   ├── FriendListScreen.kt     (新增：好友列表)
│   │   ├── FriendListViewModel.kt  (新增)
│   │   ├── AddFriendScreen.kt      (新增：搜索添加好友)
│   │   ├── AddFriendViewModel.kt   (新增)
│   │   └── FriendRequestsScreen.kt (已有，需调整)
│   ├── group/
│   │   ├── GroupManageScreen.kt     (新增：群管理邀请/踢/解散)
│   │   ├── GroupManageViewModel.kt (新增)
│   │   ├── GroupMemberScreen.kt    (新增：成员列表)
│   │   └── GroupCreateScreen.kt    (已有)
│   ├── settings/
│   │   ├── SettingsScreen.kt       (新增：设置页)
│   │   └── SettingsViewModel.kt    (新增)
│   └── navigation/
│       └── NavGraph.kt             (修改：增加新路由)
├── data/
│   └── repository/
│       └── UserRepository.kt       (新增：用户搜索)
├── crypto/
│   ├── DoubleRatchetSession.kt     (新增：E2EE核心)
│   └── E2eeManager.kt             (新增：密钥管理）
```

### 3.2 通讯录Tab（BottomNavigation第2个Tab）

```kotlin
// NavGraph.kt 修改
composable(Routes.CONTACTS) { ContactsTab(navController) }
composable(Routes.ADD_FRIEND) { AddFriendScreen(navController) }
composable(Routes.GROUP_MANAGE) { GroupManageScreen(navController) }
composable(Routes.GROUP_MEMBERS) { GroupMemberScreen(navController) }
composable(Routes.SETTINGS) { SettingsScreen(navController) }
```

### 3.3 ApiService新增方法

```kotlin
@GET("/api/user/search")
suspend fun searchUsers(@Query("q") query: String): List<UserDto>

@GET("/api/friend/list")
suspend fun getFriendList(): List<FriendDto>

@GET("/api/group/{groupId}/members")
suspend fun getGroupMembers(@Path("groupId") groupId: Long): List<MemberDto>
```

### 3.4 E2EE（Android端）

使用Tink或Bouncy Castle实现X25519 + AES-256-GCM：

```kotlin
// DoubleRatchetSession.kt — 移植服务端逻辑
class DoubleRatchetSession {
    fun initAsSender(...) { ... }
    fun initAsReceiver(...) { ... }
    fun encrypt(plain: ByteArray): EncryptResult { ... }
    fun decrypt(cipher: ByteArray, iv: ByteArray, dhPub: ByteArray, msgNum: Int, prevChainLength: Int): ByteArray { ... }
}
```

依赖：`implementation("com.google.crypto.tink:tink-android:1.13.0")` 或 `implementation("org.bouncycastle:bcprov-jdk18on:1.78")`

## 4. PC客户端补齐方案（JavaFX）

### 4.1 新增/修改文件

```
client-pc/src/main/java/com/im/client/
├── controller/
│   └── MainController.java    (修改：增加好友列表/搜索/设置Tab)
├── service/
│   ├── FriendService.java     (修改：增加好友列表+搜索方法)
│   └── E2eeService.java       (新增：E2EE加密服务)
├── model/
│   └── UserModel.java         (修改：增加nickname/avatarUrl)
├── crypto/
│   └── DoubleRatchetSession.java (新增：从server模块复制+适配)
```

### 4.2 Main.fxml修改

```xml
<!-- 在现有TabPane旁增加Tab -->
<Tab text="Friends">
  <content><ListView fx:id="friendListView"/></content>
</Tab>
<Tab text="Search">
  <content><VBox><TextField fx:id="searchField"/><ListView fx:id="searchResultListView"/></VBox>
</Tab>
<Tab text="Settings">
  <content><!-- 设置页内容 --></content>
</Tab>
```

### 4.3 深色模式

```java
// 切换方法
private void toggleDarkMode(boolean dark) {
    ObservableList<String> styles = scene.getStylesheets();
    if (dark) styles.add(getClass().getResource("/dark.css").toExternalForm());
    else styles.removeIf(s -> s.contains("dark.css"));
}
```

新增 `client-pc/src/main/resources/dark.css`：
```css
.root { -fx-base: #1a1a1a; -fx-text-fill: #e0e0e0; }
.list-cell { -fx-background-color: #2a2a2a; }
/* 覆盖所有组件颜色 */
```

### 4.4 好友列表+搜索

```java
// FriendService.java 新增
public ObservableList<UserModel> friendList = FXCollections.observableArrayList();
public void loadFriendList() { /* GET /api/friend/list */ }
public void searchUsers(String keyword) { /* GET /api/user/search?q=xxx */ }
public void applyFriend(long targetUserId) { /* POST /api/friend/apply */ }
```

### 4.5 E2EE（PC端）

直接依赖服务端模块的DoubleRatchetSession（同一个Gradle项目可引用）：

```java
import com.im.server.e2ee.DoubleRatchetSession;

class E2eeService {
    private Map<String, DoubleRatchetSession> sessions = new ConcurrentHashMap<>();

    void initSession(long remoteUserId) {
        // 1. GET /api/e2ee/keys/:userId 获取对方PreKey Bundle
        // 2. DoubleRatchetSession.initAsSender(...)
    }

    EncryptResult encrypt(long remoteUserId, byte[] plaintext) {
        return sessions.get(key).encrypt(plaintext);
    }

    byte[] decrypt(long remoteUserId, EncryptResult enc) {
        return sessions.get(key).decrypt(...);
    }
}
```

## 5. 部署配置

### 单机部署
```bash
IM_JWT_SECRET=your-secret docker-compose -f docker-compose.standalone.yml up -d
```
- 无Nacos、无Folkmq、无监控栈
- Redis有密码、MySQL有密码
- 1副本im-server + client-web

### 集群部署
```bash
IM_JWT_SECRET=your-secret docker-compose -f docker-compose.cluster.yml up -d
```
- 3节点Nacos Raft集群
- Folkmq持久化消息投递
- Prometheus + Grafana + Loki监控
- 2副本im-server + client-web
