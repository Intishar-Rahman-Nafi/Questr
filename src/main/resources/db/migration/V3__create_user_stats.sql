-- V3: Create user_stats table (gamification counters per user)

CREATE TABLE user_stats (
    id                 UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID    UNIQUE NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    total_xp           INTEGER NOT NULL DEFAULT 0,
    level              INTEGER NOT NULL DEFAULT 1,
    current_streak     INTEGER NOT NULL DEFAULT 0,
    longest_streak     INTEGER NOT NULL DEFAULT 0,
    tasks_completed    INTEGER NOT NULL DEFAULT 0,
    last_activity_date DATE
);

CREATE INDEX idx_user_stats_user_id  ON user_stats (user_id);
CREATE INDEX idx_user_stats_total_xp ON user_stats (total_xp DESC);

