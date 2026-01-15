# Phoebe Agent Service

一个基于 Spring Boot 3 的智能笔记和 AI 对话服务，提供笔记录入和 SSE 流式对话功能。

## 技术栈

- **Java 17**
- **Spring Boot 3.2.x**
- **Spring WebFlux** - 响应式 Web 框架
- **Spring Data R2DBC** - 响应式数据库访问
- **H2 Database** - 开发环境内存数据库（可切换到 PostgreSQL）
- **DashScope SDK 2.16.4** - 阿里云百炼大模型官方 Java SDK

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.8+
- DashScope API Key（从阿里云百炼控制台获取）

### 配置 API Key

设置环境变量：

```bash
export DASHSCOPE_API_KEY=your_api_key_here
```

或者在 `application.yml` 中直接配置（不推荐用于生产环境）。

### 启动服务

```bash
# 编译项目
mvn clean package -DskipTests

# 运行服务
mvn spring-boot:run

# 或者直接运行 jar
java -jar target/phoebe-service-1.0.0-SNAPSHOT.jar
```

服务默认运行在 `http://localhost:8080`

## API 接口

### 1. 健康检查

```bash
curl http://localhost:8080/health
```

响应：
```json
{"status":"ok"}
```

### 2. 笔记录入

```bash
curl -X POST http://localhost:8080/api/v1/notes \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "source": "chrome",
    "title": "测试笔记",
    "content": "这是一条测试笔记内容",
    "tags": ["test", "demo"],
    "createdAt": "2026-01-09T10:00:00+08:00"
  }'
```

响应：
```json
{"id":"uuid-string","status":"stored"}
```

### 3. 查询笔记

查询用户的所有活跃笔记：
```bash
curl http://localhost:8080/api/v1/notes \
  -H "X-User-Id: user-123"
```

按来源筛选：
```bash
curl "http://localhost:8080/api/v1/notes?source=chrome" \
  -H "X-User-Id: user-123"
```

### 4. 删除笔记（软删除）

```bash
curl -X DELETE http://localhost:8080/api/v1/notes/{noteId} \
  -H "X-User-Id: user-123"
```

响应：
```json
{"id":"note-uuid","status":"deleted"}
```

### 5. SSE 流式对话（支持 RAG）

```bash
curl -X POST http://localhost:8080/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "sessionId": "optional-session-id",
    "userId": "user-123",
    "message": "你好，请介绍一下自己",
    "enableRag": true
  }'
```

**请求参数说明：**
- `userId`: 用户ID（必填），用于检索用户的个人知识库
- `message`: 用户消息（必填）
- `sessionId`: 会话ID（可选）
- `enableRag`: 是否启用知识库检索增强（默认 true）

当 `enableRag` 为 true 时，系统会先从用户的个人知识库中检索相关信息，然后将这些信息作为上下文一起发送给大模型，使回答更加个性化和准确。

响应（SSE 事件流）：
```
event: token
data: {"delta":"你好"}

event: token
data: {"delta":"！"}

event: token
data: {"delta":"我是"}

...

event: done
data: {"usage":{"prompt_tokens":10,"completion_tokens":50,"total_tokens":60}}
```

### 6. 知识库管理

#### 获取用户知识库信息
```bash
curl http://localhost:8080/api/knowledge-base/user/user-123
```

#### 手动同步用户笔记到知识库
```bash
curl -X POST http://localhost:8080/api/knowledge-base/user/user-123/sync
```

响应：
```json
{"userId":"user-123","syncedCount":5,"message":"Sync completed successfully"}
```

#### 强制重新同步所有笔记
```bash
curl -X POST http://localhost:8080/api/knowledge-base/user/user-123/force-sync
```

#### 检查笔记同步状态
```bash
curl http://localhost:8080/api/knowledge-base/note/{noteId}/sync-status
```

#### 触发全局同步（所有用户）
```bash
curl -X POST http://localhost:8080/api/knowledge-base/sync-all
```

## 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SERVER_PORT` | 服务端口 | 8080 |
| `DASHSCOPE_API_KEY` | DashScope API Key | - |
| `DASHSCOPE_MODEL` | 使用的模型 | qwen-turbo |
| `DASHSCOPE_TIMEOUT` | API 超时时间（秒） | 60 |
| `R2DBC_URL` | 数据库连接 URL | r2dbc:h2:mem:///phoebedb |
| `R2DBC_USERNAME` | 数据库用户名 | sa |
| `R2DBC_PASSWORD` | 数据库密码 | - |
| `BAILIAN_ACCESS_KEY_ID` | 阿里云 AccessKey ID | - |
| `BAILIAN_ACCESS_KEY_SECRET` | 阿里云 AccessKey Secret | - |
| `BAILIAN_WORKSPACE_ID` | 百炼业务空间 ID | - |
| `BAILIAN_REGION` | 百炼服务区域 | cn-beijing |
| `BAILIAN_SYNC_ENABLED` | 是否启用知识库同步 | true |
| `BAILIAN_SYNC_CRON` | 同步 Cron 表达式 | 0 0 2 * * ? |
| `BAILIAN_RETRIEVE_TOP_K` | 检索返回数量 | 5 |

### 切换到 PostgreSQL

1. 修改 `pom.xml`，取消 PostgreSQL 依赖的注释
2. 设置环境变量：

```bash
export R2DBC_URL=r2dbc:postgresql://localhost:5432/phoebe
export R2DBC_USERNAME=your_username
export R2DBC_PASSWORD=your_password
```

## 项目结构

```
src/main/java/com/phoebe/
├── PhoebeServiceApplication.java    # 主应用类
├── config/
│   ├── BailianConfig.java           # 百炼知识库配置
│   ├── DashScopeConfig.java         # DashScope 配置
│   └── DatabaseConfig.java          # 数据库初始化配置
├── controller/
│   ├── ChatController.java          # 对话接口
│   ├── HealthController.java        # 健康检查接口
│   ├── KnowledgeBaseController.java # 知识库管理接口
│   └── NoteController.java          # 笔记接口
├── dto/
│   ├── ChatRequest.java             # 对话请求 DTO
│   ├── NoteRequest.java             # 笔记请求 DTO
│   ├── NoteResponse.java            # 笔记响应 DTO
│   └── bailian/
│       └── RetrieveResult.java      # 知识库检索结果 DTO
├── entity/
│   ├── Note.java                    # 笔记实体
│   ├── NoteSyncHistory.java         # 笔记同步历史实体
│   └── UserKnowledgeBase.java       # 用户知识库映射实体
├── exception/
│   └── GlobalExceptionHandler.java  # 全局异常处理
├── repository/
│   ├── NoteRepository.java          # 笔记仓库
│   ├── NoteSyncHistoryRepository.java  # 同步历史仓库
│   └── UserKnowledgeBaseRepository.java # 知识库映射仓库
├── scheduler/
│   └── NotesSyncScheduler.java      # 定时同步任务
└── service/
    ├── BailianKnowledgeService.java # 百炼知识库服务
    ├── ChatService.java             # 对话服务（集成 RAG）
    └── NoteService.java             # 笔记服务
```

## 数据库表结构

```sql
-- 笔记表
CREATE TABLE notes (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,     -- 用户ID
    source VARCHAR(20) NOT NULL,
    title VARCHAR(500),
    content TEXT NOT NULL,
    comment TEXT,                     -- 记录者的评论
    tags TEXT,                        -- JSON 数组格式
    status INT NOT NULL DEFAULT 1,    -- 0: 已删除, 1: 正常
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 用户知识库映射表
CREATE TABLE user_knowledge_base (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    index_id VARCHAR(64) NOT NULL,    -- 百炼知识库索引ID
    workspace_id VARCHAR(64),         -- 百炼业务空间ID
    index_name VARCHAR(255),          -- 知识库名称
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_sync_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 笔记同步历史表
CREATE TABLE note_sync_history (
    id VARCHAR(36) PRIMARY KEY,
    note_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    index_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64),          -- 百炼返回的文档ID
    sync_status VARCHAR(20) NOT NULL, -- PENDING, SUCCESS, FAILED
    error_message TEXT,
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 索引
CREATE INDEX idx_notes_user_id ON notes(user_id);
CREATE INDEX idx_notes_user_status ON notes(user_id, status);
CREATE INDEX idx_user_kb_user_id ON user_knowledge_base(user_id);
CREATE INDEX idx_note_sync_note_id ON note_sync_history(note_id);
```

## iOS 客户端集成示例

使用 Alamofire 或原生 URLSession 消费 SSE：

```swift
import Foundation

class ChatClient {
    func streamChat(message: String) {
        guard let url = URL(string: "http://localhost:8080/api/v1/chat/stream") else { return }
        
var request = URLRequest(url: url)
request.httpMethod = "POST"
request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        
        let body = ["message": message]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            // 处理 SSE 事件流
        }
        task.resume()
    }
}
```

## 开发计划

- [x] M0: 基础骨架
  - [x] 健康检查接口
  - [x] 笔记录入接口
  - [x] SSE 流式对话接口
- [x] M1: RAG 集成
  - [x] 接入百炼知识库
  - [x] 笔记向量化存储
  - [x] 用户知识库隔离
  - [x] 每日定时同步任务
  - [x] 知识库检索增强对话
- [ ] M2: 定时任务
  - [ ] 生成每日/周总结
  - [ ] MCP Publisher 输出

## 知识库 RAG 功能说明

### 工作流程

1. **笔记录入**: 用户通过 API 录入笔记
2. **定时同步**: 每天凌晨 2 点自动将未同步的笔记导入到用户的百炼知识库
3. **知识检索**: 用户发起对话时，系统先从其知识库检索相关内容
4. **增强回答**: 将检索到的知识作为上下文提供给大模型，生成更个性化的回答

### 数据隔离

- 每个用户拥有独立的知识库（通过 `user_knowledge_base` 表维护映射关系）
- 用户 A 的笔记只会进入 A 的知识库，不会与其他用户混淆
- 对话时只检索当前用户的知识库

### 配置百炼

1. 登录[阿里云百炼控制台](https://bailian.console.aliyun.com/)
2. 创建业务空间，获取 workspace ID
3. 获取 AccessKey（建议使用 RAM 子账号）
4. 配置环境变量：

```bash
export BAILIAN_ACCESS_KEY_ID=your_access_key_id
export BAILIAN_ACCESS_KEY_SECRET=your_access_key_secret
export BAILIAN_WORKSPACE_ID=your_workspace_id
```

## License

MIT
