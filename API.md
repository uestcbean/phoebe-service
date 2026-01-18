# Phoebe Service API 文档（当前工程）

> 面向前端页面对接。本文档基于代码中 `Controller`/DTO 的实际定义整理而来。
>
> - **Base URL**: `http://localhost:8080`
> - **数据格式**: JSON（除 SSE 接口为 `text/event-stream`）
> - **ID 类型**: 数据库与接口中的用户/笔记等 ID 目前统一使用 **`Long`（BIGINT）**

---

## 认证机制

所有 API（除登录/注册接口外）都需要携带认证 token。

### Token 获取

通过 `/api/v1/users/login` 登录成功后，响应中会包含 `token` 字段。

### 请求认证

在每个请求的 Header 中携带：

```
Authorization: Bearer <token>
```

### 未认证错误

如果未提供 token 或 token 无效，API 返回：

- **状态码**: 401 Unauthorized
- **响应体**: `{ "error": "未提供认证令牌" }` 或 `{ "error": "认证令牌无效或已过期" }`

---

## 通用约定

### 通用 Header

- **`Content-Type: application/json`**: 发送 JSON body 时必带
- **`Accept: text/event-stream`**: 调用 SSE 流式接口时建议带
- **`Authorization: Bearer <token>`**: 除登录/注册外的所有接口必带

### 通用错误返回

工程中全局异常处理器会在发生未捕获异常时返回：

```json
{
  "error": "Internal server error",
  "message": "具体异常信息"
}
```

参数校验失败（JSR-303 校验）会返回：

```json
{
  "error": "Validation failed",
  "details": {
    "fieldName": "错误原因"
  }
}
```

> 注意：部分 Controller 里也有 `badRequest()` 的返回，可能只包含 `{ "error": "..." }` 或直接空 body（例如索引池 addIndex 里捕获后 `badRequest().build()`）。

---

## Health

### GET `/health`

**说明**: 健康检查。

**响应**（200）:

```json
{ "status": "ok" }
```

---

## Chat（SSE 流式）

### POST `/api/v1/chat/stream`

**说明**: 流式对话（SSE）。服务端返回 `text/event-stream`，前端需要按流消费。**用户 ID 自动从 token 中获取。**

**请求体**（`ChatRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `sessionId` | string | 否 | 会话 ID（可选） |
| `message` | string | 是 | 用户输入 |
| `enableRag` | boolean | 否 | 是否启用 RAG（默认 `true`） |
| `history` | array | 否 | 对话历史，用于多轮对话 |

**curl 示例**（推荐单行，避免换行转义问题）:

```bash
curl -N -X POST "http://localhost:8080/api/v1/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  --data-raw '{"sessionId":"demo-session","message":"对各种 AI Coding Agent 工具的看法","enableRag":true}'
```

**响应**: `text/event-stream`（事件流；每条 event 的 data 为字符串）

---

## Notes（笔记）

### POST `/api/v1/notes`

**说明**: 创建笔记。**用户 ID 自动从 token 中获取。**

**请求体**（`NoteRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `source` | string | 是 | 来源（例如 `flomo`/`manual` 等） |
| `title` | string | 否 | 标题 |
| `content` | string | 是 | 内容 |
| `comment` | string | 否 | 评论/备注 |
| `tags` | string[] | 否 | 标签数组 |
| `createdAt` | string(ISO-8601, OffsetDateTime) | 是 | 例如 `2026-01-09T10:00:00+08:00` |

**响应**（201，`NoteResponse`）:

```json
{ "id": 123, "status": "stored" }
```

---

### GET `/api/v1/notes`

**说明**: 获取当前用户的「活跃」笔记列表，可按 `source` 过滤。**用户 ID 自动从 token 中获取。**

**Query**:

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `source` | string | 否 | 按来源过滤 |

**响应**（200）: `Note[]`

> `Note` 为后端实体对象，字段以实体定义为准（含 `id/userId/source/title/content/status/createdAt/...`）。

---

### DELETE `/api/v1/notes/{noteId}`

**说明**: 软删除笔记。**只能删除当前用户的笔记。**

**Path**:

| 参数 | 类型 | 必填 |
|---|---|---:|
| `noteId` | number(Long) | 是 |

**响应**（200，`NoteResponse`）:

```json
{ "id": 123, "status": "deleted" }
```

（实际 `status` 由服务实现返回，通常表示删除成功）

---

## Users（用户管理）

### POST `/api/v1/users/login` 🔓

**说明**: 用户登录，返回认证 token。**此接口不需要认证。**

**请求体**（`LoginRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `username` | string | 是 | 用户名 |
| `password` | string | 是 | 密码（6-100字符） |

**响应**（200，`LoginResponse`）:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id": 2,
  "username": "admin",
  "nickname": "管理员",
  "email": "admin@example.com",
  "phone": null,
  "avatarUrl": null
}
```

**失败响应**（401）:

```json
{ "error": "用户名或密码错误" }
```

---

### POST `/api/v1/users/logout`

**说明**: 用户登出，使当前 token 失效。

**响应**（200）:

```json
{ "message": "Logged out successfully" }
```

---

### GET `/api/v1/users/me`

**说明**: 获取当前登录用户信息。

**响应**（200）: `User`

---

### POST `/api/v1/users`

**说明**: 创建用户（注册）。注册时会尝试从索引池为用户分配 Bailian `indexId/categoryId`（具体逻辑在 `UserService` / `BailianIndexPoolService` 中）。

**请求体**（`UserRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `username` | string | 是 | 2-50 字符 |
| `password` | string | 是 | 密码（6-100字符） |
| `email` | string | 否 | 邮箱 |
| `phone` | string | 否 | 手机 |
| `nickname` | string | 否 | 昵称 |
| `avatarUrl` | string | 否 | 头像 URL |

**响应**（201）: `User`

> `User` 返回包含：`id/username/email/phone/nickname/avatarUrl/status/createdAt/updatedAt/lastLoginAt` 等字段（以实体为准）。

**失败响应**（400）:

```json
{ "error": "原因" }
```

---

### GET `/api/v1/users/{id}`

**说明**: 按 ID 获取用户。

**响应**:

- 200: `User`
- 404: 空 body

---

### GET `/api/v1/users/username/{username}`

**说明**: 按 username 获取用户。

**响应**:

- 200: `User`
- 404: 空 body

---

### GET `/api/v1/users`

**说明**: 获取用户列表，支持按状态过滤或关键字搜索。

**Query**:

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `status` | number(int) | 否 | 例如 `1` 活跃、`0` 禁用 |
| `keyword` | string | 否 | 关键字搜索（优先于 status） |

**响应**（200）: `User[]`

---

### PUT `/api/v1/users/{id}`

**说明**: 更新用户信息。

**请求体**: 同 `UserRequest`

**响应**:

- 200: `User`
- 400: `{ "error": "..." }`

---

### POST `/api/v1/users/{id}/enable`

**说明**: 启用用户。

**响应**:

- 200: `User`
- 400: `{ "error": "..." }`

---

### POST `/api/v1/users/{id}/disable`

**说明**: 禁用用户。

**响应**:

- 200: `User`
- 400: `{ "error": "..." }`

---

### DELETE `/api/v1/users/{id}`

**说明**: 删除用户。

**响应**（200）:

```json
{ "message": "User deleted successfully", "id": 2 }
```

失败（400）:

```json
{ "error": "..." }
```

---

### GET `/api/v1/users/stats`

**说明**: 用户统计。

**响应**（200）:

```json
{ "total": 10, "active": 9, "disabled": 1 }
```

---

## 知识库（Knowledge Base）

Base Path: `/api/knowledge-base`

> **所有知识库接口现在从登录 token 自动获取用户 ID**

### GET `/api/knowledge-base`

**说明**: 获取当前用户知识库信息，不存在则创建/初始化。

**响应**（200）: `UserKnowledgeBase`

> `UserKnowledgeBase` 主要包含该用户绑定的 `indexId` 等信息（以实体定义为准）。

---

### POST `/api/knowledge-base/sync`

**说明**: 手动触发同步：将当前用户未同步的笔记同步到 Bailian 知识库。

**响应**（200）:

```json
{ "userId": 2, "syncedCount": 3, "message": "Sync completed successfully" }
```

失败（500）:

```json
{ "error": "..." }
```

---

### POST `/api/knowledge-base/force-sync`

**说明**: 强制同步所有笔记（历史注释写了"可能产生重复文档"，请谨慎使用）。

**响应**（200）:

```json
{ "userId": 2, "syncedCount": 10, "message": "Force sync completed successfully" }
```

---

### GET `/api/knowledge-base/note/{noteId}/sync-status`

**说明**: 查询某条笔记是否已同步过。

**响应**（200）:

```json
{ "noteId": 123, "synced": true }
```

---

### POST `/api/knowledge-base/note/{noteId}/update`

**说明**: 更新已同步的笔记到知识库（实现为“删除旧文档 + 上传新内容”）。

**响应**（200）:

```json
{
  "noteId": 123,
  "success": true,
  "documentId": "xxx",
  "message": "Note updated successfully in knowledge base"
}
```

可能返回：

- 404：笔记不存在
- 400：笔记非 active（body 为 `{ "error": "...", "noteId": ... }`）

---

### POST `/api/knowledge-base/update-synced`

**说明**: 批量更新：将当前用户所有「曾经同步成功」的活跃笔记按最新内容更新到知识库。

**响应**（200）:

```json
{ "userId": 2, "updatedCount": 5, "failedCount": 1, "message": "Batch update completed" }
```

---

### POST `/api/knowledge-base/sync-all`

**说明**: 手动触发全量同步（所有用户），后台线程执行。

**响应**（200）:

```json
{ "message": "Global sync started in background" }
```

---

### GET `/api/knowledge-base/notes-status`

**说明**: Debug：查看当前用户笔记状态摘要（包含 active/total 统计与笔记简要列表）。

**响应**（200）: `Map<String,Object>`（结构见实现）

---

## 索引池管理（Index Pool，管理接口）

Base Path: `/api/admin/index-pool`

> 这组接口目前没有显式鉴权（生产建议加权限控制）。

### POST `/api/admin/index-pool`

**说明**: 添加单条索引到池中。

**请求体**（`AddIndexRequest`）:

| 字段 | 类型 | 必填 |
|---|---|---:|
| `indexId` | string | 是 |
| `categoryId` | string | 是 |
| `indexName` | string | 否 |

**响应**（200）: `BailianIndexPool`

---

### POST `/api/admin/index-pool/batch`

**说明**: 批量添加索引到池中。

**请求体**: `AddIndexRequest[]`

**响应**（200）:

```json
{ "requested": 10, "added": 10, "message": "Batch add completed" }
```

---

### GET `/api/admin/index-pool`

**说明**: 查询索引池全部条目。

**响应**（200）: `BailianIndexPool[]`

---

### GET `/api/admin/index-pool/stats`

**说明**: 索引池统计。

**响应**（200）: `PoolStats`

---

### GET `/api/admin/index-pool/available`

**说明**: 查询可用（AVAILABLE）索引列表。

**响应**（200）: `BailianIndexPool[]`

---

### GET `/api/admin/index-pool/assigned`

**说明**: 查询已分配（ASSIGNED）索引列表。

**响应**（200）: `BailianIndexPool[]`

---

### GET `/api/admin/index-pool/user/{userId}`

**说明**: 查询某个用户已分配的索引。

**响应**:

- 200: `BailianIndexPool`
- 404: 空 body

---

### POST `/api/admin/index-pool/assign/{userId}`

**说明**: 给用户分配索引（从池中挑选可用索引并标记为 ASSIGNED）。

**响应**（200）:

```json
{
  "userId": 2,
  "indexId": "m71tmd04g9",
  "categoryId": "cate_xxx",
  "message": "Index assigned successfully"
}
```

失败（400）:

```json
{ "error": "..." }
```

---

### POST `/api/admin/index-pool/release/{userId}`

**说明**: 释放某个用户占用的索引（回收回 AVAILABLE）。

**响应**（200）:

```json
{ "message": "Index released successfully" }
```

---

### POST `/api/admin/index-pool/{id}/disable`

**说明**: 禁用索引（DISABLED）。

**响应**（200）:

```json
{ "message": "Index disabled successfully" }
```

---

### POST `/api/admin/index-pool/{id}/enable`

**说明**: 启用索引（通常回到 AVAILABLE，具体以 service 实现为准）。

**响应**（200）:

```json
{ "message": "Index enabled successfully" }
```

---

### DELETE `/api/admin/index-pool/{id}`

**说明**: 删除索引池条目。

**响应**（200）:

```json
{ "message": "Index deleted successfully" }
```

失败（400）:

```json
{ "error": "..." }
```

---

## Chat Sessions（对话会话持久化）

Base Path: `/api/v1/chat/sessions`

> 会话和消息会存储到数据库中，支持跨设备访问历史记录。
> **所有会话接口现在从登录 token 自动获取用户 ID**

### GET `/api/v1/chat/sessions`

**说明**: 获取当前用户所有会话及其消息。

**响应**（200）: `ChatSession[]`

每个 `ChatSession` 包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number(Long) | 数据库 ID |
| `sessionId` | string | 会话唯一标识 |
| `userId` | number(Long) | 用户 ID |
| `title` | string | 会话标题（从第一条用户消息生成） |
| `createdAt` | string(ISO-8601) | 创建时间 |
| `updatedAt` | string(ISO-8601) | 最后更新时间 |
| `messages` | ChatMessage[] | 该会话的所有消息 |

每个 `ChatMessage` 包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number(Long) | 消息 ID |
| `sessionId` | number(Long) | 关联的会话数据库 ID |
| `role` | string | `user` 或 `assistant` |
| `content` | string | 消息内容 |
| `createdAt` | string(ISO-8601) | 发送时间 |

---

### GET `/api/v1/chat/sessions/list`

**说明**: 获取当前用户所有会话列表（不含消息，用于侧边栏展示）。

**响应**（200）: `ChatSession[]`（不含 `messages` 字段）

---

### GET `/api/v1/chat/sessions/{sessionId}`

**说明**: 根据 sessionId 获取单个会话及其消息。

**响应**:

- 200: `ChatSession`（含 `messages`）
- 404: 空 body

---

### POST `/api/v1/chat/sessions`

**说明**: 保存或更新会话及其消息。如果会话已存在，会替换其消息。**用户 ID 自动从 token 中获取。**

**请求体**（`SaveSessionRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `sessionId` | string | 是 | 会话唯一标识 |
| `title` | string | 否 | 会话标题 |
| `messages` | ChatMessage[] | 否 | 消息列表 |

**请求示例**:

```json
{
  "sessionId": "session-1737123456789-abc123",
  "title": "关于AI Coding的讨论",
  "messages": [
    { "role": "user", "content": "你好" },
    { "role": "assistant", "content": "你好！有什么我可以帮助你的吗？" }
  ]
}
```

**响应**（200）: `ChatSession`

---

### POST `/api/v1/chat/sessions/{sessionId}/messages`

**说明**: 向指定会话添加单条消息。如果会话不存在，会自动创建。**用户 ID 自动从 token 中获取。**

**请求体**（`AddMessageRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `role` | string | 是 | `user` 或 `assistant` |
| `content` | string | 是 | 消息内容 |

**响应**（200）: `ChatMessage`

---

### PUT `/api/v1/chat/sessions/{sessionId}/title`

**说明**: 更新会话标题。

**请求体**:

```json
{ "title": "新标题" }
```

**响应**（200）:

```json
{ "message": "Title updated" }
```

---

### DELETE `/api/v1/chat/sessions/{sessionId}`

**说明**: 删除会话及其所有消息。

**响应**（200）:

```json
{ "message": "Session deleted" }
```


