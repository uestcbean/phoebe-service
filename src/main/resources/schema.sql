-- Notes table for storing user notes
CREATE TABLE IF NOT EXISTS notes (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    source VARCHAR(20) NOT NULL,
    title VARCHAR(500),
    content TEXT NOT NULL,
    comment TEXT,                     -- 记录者的评论
    tags TEXT,
    status INT NOT NULL DEFAULT 1,  -- 0: deleted, 1: active
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_notes_user_id ON notes(user_id);
CREATE INDEX IF NOT EXISTS idx_notes_user_status ON notes(user_id, status);
CREATE INDEX IF NOT EXISTS idx_notes_source ON notes(source);
CREATE INDEX IF NOT EXISTS idx_notes_created_at ON notes(created_at);

-- User Knowledge Base mapping table
-- Stores the relationship between users and their Bailian knowledge bases
CREATE TABLE IF NOT EXISTS user_knowledge_base (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    index_id VARCHAR(64) NOT NULL,              -- 百炼知识库索引ID
    workspace_id VARCHAR(64),                   -- 百炼业务空间ID
    index_name VARCHAR(255),                    -- 知识库名称
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, DISABLED, ERROR
    last_sync_at TIMESTAMP WITH TIME ZONE,      -- 上次同步时间
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_kb_user_id ON user_knowledge_base(user_id);
CREATE INDEX IF NOT EXISTS idx_user_kb_index_id ON user_knowledge_base(index_id);

-- Note sync history table
-- Tracks which notes have been synced to knowledge base
CREATE TABLE IF NOT EXISTS note_sync_history (
    id VARCHAR(36) PRIMARY KEY,
    note_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    index_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64),                    -- 百炼返回的文档ID
    sync_status VARCHAR(20) NOT NULL,           -- PENDING, SUCCESS, FAILED
    error_message TEXT,
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_note_sync_note_id ON note_sync_history(note_id);
CREATE INDEX IF NOT EXISTS idx_note_sync_user_id ON note_sync_history(user_id);
CREATE INDEX IF NOT EXISTS idx_note_sync_status ON note_sync_history(sync_status);
