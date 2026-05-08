-- V5: Create challenges and challenge_participants tables

CREATE TABLE challenges (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    invite_code VARCHAR(20)  UNIQUE NOT NULL,
    start_date  TIMESTAMP    NOT NULL,
    end_date    TIMESTAMP    NOT NULL,
    target_xp   INTEGER      NOT NULL DEFAULT 100,
    created_by  UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_challenges_invite_code ON challenges (invite_code);
CREATE INDEX idx_challenges_created_by  ON challenges (created_by);
CREATE INDEX idx_challenges_end_date    ON challenges (end_date);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE challenge_participants (
    challenge_id UUID      NOT NULL REFERENCES challenges (id) ON DELETE CASCADE,
    user_id      UUID      NOT NULL REFERENCES users      (id) ON DELETE CASCADE,
    current_xp   INTEGER   NOT NULL DEFAULT 0,
    joined_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (challenge_id, user_id)
);

CREATE INDEX idx_challenge_participants_user ON challenge_participants (user_id);

