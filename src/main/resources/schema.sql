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
