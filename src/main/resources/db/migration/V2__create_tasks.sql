-- V2: Create tasks table

CREATE TABLE tasks (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    category     VARCHAR(50),
    priority     VARCHAR(20),
    deadline     TIMESTAMP,
    completed    BOOLEAN      NOT NULL DEFAULT FALSE,
    xp_value     INTEGER      NOT NULL DEFAULT 10,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_tasks_user_id    ON tasks (user_id);
CREATE INDEX idx_tasks_completed  ON tasks (user_id, completed);
CREATE INDEX idx_tasks_created_at ON tasks (user_id, created_at DESC);

