-- MySQL Schema for Phoebe Service
-- Note: Run this script manually or use Flyway/Liquibase for migrations
-- All ID fields use BIGINT with AUTO_INCREMENT

-- Users table for user management
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,         -- 登录密码（建议存储加密后的值）
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    nickname VARCHAR(100),
    avatar_url VARCHAR(500),
    status INT NOT NULL DEFAULT 1,          -- 0: disabled, 1: active
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at DATETIME,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email),
    INDEX idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bailian Index Pool - stores available knowledge base index IDs
-- Each user registration consumes one index ID from this pool
CREATE TABLE IF NOT EXISTS bailian_index_pool (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    index_id VARCHAR(64) NOT NULL UNIQUE,           -- 百炼知识库索引ID (e.g., m71tmd04g9)
    category_id VARCHAR(64) NOT NULL,               -- 百炼数据中心类目ID
    index_name VARCHAR(255),                         -- 索引名称描述
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, ASSIGNED, DISABLED
    assigned_user_id BIGINT,                        -- 分配给的用户ID
    assigned_at DATETIME,                           -- 分配时间
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_index_pool_status (status),
    INDEX idx_index_pool_index_id (index_id),
    INDEX idx_index_pool_assigned_user (assigned_user_id),
    CONSTRAINT fk_index_pool_user FOREIGN KEY (assigned_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Notes table for storing user notes
CREATE TABLE IF NOT EXISTS notes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    title VARCHAR(500),
    content TEXT NOT NULL,
    comment TEXT,                     -- 记录者的评论
    tags TEXT,
    status INT NOT NULL DEFAULT 1,    -- 0: deleted, 1: active
    created_at DATETIME NOT NULL,
    ingested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notes_user_id (user_id),
    INDEX idx_notes_user_status (user_id, status),
    INDEX idx_notes_source (source),
    INDEX idx_notes_created_at (created_at),
    CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User Knowledge Base mapping table
-- Stores the relationship between users and their Bailian knowledge bases
CREATE TABLE IF NOT EXISTS user_knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    index_id VARCHAR(64) NOT NULL,              -- 百炼知识库索引ID
    workspace_id VARCHAR(64),                   -- 百炼业务空间ID
    index_name VARCHAR(255),                    -- 知识库名称
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, DISABLED, ERROR
    last_sync_at DATETIME,                      -- 上次同步时间
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_kb_user_id (user_id),
    INDEX idx_user_kb_index_id (index_id),
    CONSTRAINT fk_user_kb_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Note sync history table
-- Tracks which notes have been synced to knowledge base
CREATE TABLE IF NOT EXISTS note_sync_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    index_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64),                    -- 百炼返回的文档ID
    sync_status VARCHAR(20) NOT NULL,           -- PENDING, SUCCESS, FAILED
    error_message TEXT,
    synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_note_sync_note_id (note_id),
    INDEX idx_note_sync_user_id (user_id),
    INDEX idx_note_sync_status (sync_status),
    CONSTRAINT fk_note_sync_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_note_sync_note FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chat sessions table
-- Stores conversation sessions for each user

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL UNIQUE,     -- 会话唯一标识
    user_id BIGINT NOT NULL,                    -- 用户ID
    title VARCHAR(255),                         -- 会话标题（用户自定义或从第一条消息生成）
    topic VARCHAR(50),                          -- 会话主题/专注领域（如：技术、学习、日常、创作等）
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_chat_sessions_user_id (user_id),
    INDEX idx_chat_sessions_session_id (session_id),
    INDEX idx_chat_sessions_updated_at (updated_at),
    INDEX idx_chat_sessions_topic (topic),
    CONSTRAINT fk_chat_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chat messages table
-- Stores individual messages within a session
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,                 -- 关联的会话ID
    role VARCHAR(20) NOT NULL,                  -- 'user' or 'assistant'
    content TEXT NOT NULL,                      -- 消息内容
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chat_messages_session_id (session_id),
    INDEX idx_chat_messages_created_at (created_at),
    CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
