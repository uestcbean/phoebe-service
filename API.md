# Phoebe Service API 文档（当前工程）

> 面向前端页面对接。本文档基于代码中 `Controller`/DTO 的实际定义整理而来。
>
> - **Base URL**: `http://localhost:8080`
> - **数据格式**: JSON（除 SSE 接口为 `text/event-stream`）
> - **ID 类型**: 数据库与接口中的用户/笔记等 ID 目前统一使用 **`Long`（BIGINT）**

---

## 通用约定

### 通用 Header

- **`Content-Type: application/json`**: 发送 JSON body 时必带
- **`Accept: text/event-stream`**: 调用 SSE 流式接口时建议带
- **`X-User-Id: <Long>`**: 部分接口通过 Header 传入用户 ID（例如 Notes 列表/删除）

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

**说明**: 流式对话（SSE）。服务端返回 `text/event-stream`，前端需要按流消费。

**请求体**（`ChatRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `sessionId` | string | 否 | 会话 ID（可选） |
| `userId` | number(Long) | 是 | 当前用户 ID |
| `message` | string | 是 | 用户输入 |
| `enableRag` | boolean | 否 | 是否启用 RAG（默认 `true`） |

**curl 示例**（推荐单行，避免换行转义问题）:

```bash
curl -N -X POST "http://localhost:8080/api/v1/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  --data-raw '{"sessionId":"demo-session","userId":2,"message":"对各种 AI Coding Agent 工具的看法","enableRag":true}'
```

**响应**: `text/event-stream`（事件流；每条 event 的 data 为字符串）

---

## Notes（笔记）

### POST `/api/v1/notes`

**说明**: 创建笔记。

**请求体**（`NoteRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `userId` | number(Long) | 是 | 用户 ID |
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

**说明**: 获取某用户的「活跃」笔记列表，可按 `source` 过滤。

**请求 Header**:

- `X-User-Id: <Long>`（必填）

**Query**:

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `source` | string | 否 | 按来源过滤 |

**响应**（200）: `Note[]`

> `Note` 为后端实体对象，字段以实体定义为准（含 `id/userId/source/title/content/status/createdAt/...`）。

---

### DELETE `/api/v1/notes/{noteId}`

**说明**: 软删除笔记。

**Path**:

| 参数 | 类型 | 必填 |
|---|---|---:|
| `noteId` | number(Long) | 是 |

**请求 Header**:

- `X-User-Id: <Long>`（必填）

**响应**（200，`NoteResponse`）:

```json
{ "id": 123, "status": "deleted" }
```

（实际 `status` 由服务实现返回，通常表示删除成功）

---

## Users（用户管理）

### POST `/api/v1/users`

**说明**: 创建用户（注册）。注册时会尝试从索引池为用户分配 Bailian `indexId/categoryId`（具体逻辑在 `UserService` / `BailianIndexPoolService` 中）。

**请求体**（`UserRequest`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `username` | string | 是 | 2-50 字符 |
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

### GET `/api/knowledge-base/user/{userId}`

**说明**: 获取用户知识库信息，不存在则创建/初始化（逻辑在 `BailianKnowledgeService.getOrCreateKnowledgeBase`）。

**响应**（200）: `UserKnowledgeBase`

> `UserKnowledgeBase` 主要包含该用户绑定的 `indexId` 等信息（以实体定义为准）。

---

### POST `/api/knowledge-base/user/{userId}/sync`

**说明**: 手动触发同步：将该用户未同步的笔记同步到 Bailian 知识库。

**响应**（200）:

```json
{ "userId": 2, "syncedCount": 3, "message": "Sync completed successfully" }
```

失败（500）:

```json
{ "error": "..." }
```

---

### POST `/api/knowledge-base/user/{userId}/force-sync`

**说明**: 强制同步所有笔记（历史注释写了“可能产生重复文档”，请谨慎使用）。

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

### POST `/api/knowledge-base/user/{userId}/update-synced`

**说明**: 批量更新：将该用户所有「曾经同步成功」的活跃笔记按最新内容更新到知识库。

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

### GET `/api/knowledge-base/user/{userId}/notes-status`

**说明**: Debug：查看该用户笔记状态摘要（包含 active/total 统计与笔记简要列表）。

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


