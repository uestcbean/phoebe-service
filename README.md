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

### 5. SSE 流式对话

```bash
curl -X POST http://localhost:8080/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "sessionId": "optional-session-id",
    "message": "你好，请介绍一下自己"
  }'
```

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
│   ├── DashScopeConfig.java         # DashScope 配置
│   ├── DatabaseConfig.java          # 数据库初始化配置
│   └── WebClientConfig.java         # WebClient 配置
├── controller/
│   ├── HealthController.java        # 健康检查接口
│   ├── NoteController.java          # 笔记接口
│   └── ChatController.java          # 对话接口
├── dto/
│   ├── ChatRequest.java             # 对话请求 DTO
│   ├── NoteRequest.java             # 笔记请求 DTO
│   ├── NoteResponse.java            # 笔记响应 DTO
│   └── dashscope/
│       ├── DashScopeRequest.java    # DashScope 请求 DTO
│       └── DashScopeResponse.java   # DashScope 响应 DTO
├── entity/
│   └── Note.java                    # 笔记实体
├── exception/
│   └── GlobalExceptionHandler.java  # 全局异常处理
├── repository/
│   └── NoteRepository.java          # 笔记仓库
└── service/
    ├── ChatService.java             # 对话服务
    └── NoteService.java             # 笔记服务
```

## 数据库表结构

```sql
CREATE TABLE notes (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,     -- 用户ID
    source VARCHAR(20) NOT NULL,
    title VARCHAR(500),
    content TEXT NOT NULL,
    tags TEXT,                        -- JSON 数组格式
    status INT NOT NULL DEFAULT 1,    -- 0: 已删除, 1: 正常
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 索引
CREATE INDEX idx_notes_user_id ON notes(user_id);
CREATE INDEX idx_notes_user_status ON notes(user_id, status);
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
- [ ] M1: RAG 集成
  - [ ] 接入百炼知识库
  - [ ] 笔记向量化存储
- [ ] M2: 定时任务
  - [ ] 生成每日/周总结
  - [ ] MCP Publisher 输出

## License

MIT
