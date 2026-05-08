-- V4: Create badges and user_badges tables

CREATE TABLE badges (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) UNIQUE NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    description  TEXT,
    icon_url     VARCHAR(255),
    badge_type   VARCHAR(50)  NOT NULL,
    criteria     TEXT,
    reward_xp    INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_badges_name ON badges (name);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE user_badges (
    id        UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id   UUID      NOT NULL REFERENCES users  (id) ON DELETE CASCADE,
    badge_id  UUID      NOT NULL REFERENCES badges (id),
    earned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_badge UNIQUE (user_id, badge_id)
);

CREATE INDEX idx_user_badges_user_id ON user_badges (user_id);

