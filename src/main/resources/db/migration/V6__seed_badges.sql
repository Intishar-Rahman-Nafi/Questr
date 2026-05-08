-- V6: Seed the badge catalogue
-- 18 badges across 4 badge types.
-- reward_xp is the BONUS XP awarded to the user when the badge unlocks.

INSERT INTO badges (name, display_name, description, icon_url, badge_type, criteria, reward_xp) VALUES

-- ── STREAK badges ──────────────────────────────────────────────────────────
('STREAK_1',
 'First Flame',
 'Complete a task on your first day.',
 '🔥', 'STREAK',
 '{"type":"STREAK","days":1}',
 0),

('STREAK_3',
 '3-Day Warrior',
 'Maintain a 3-day task completion streak.',
 '🔥🔥', 'STREAK',
 '{"type":"STREAK","days":3}',
 50),

('STREAK_7',
 '7-Day Champion',
 'Maintain a 7-day task completion streak.',
 '🔥🔥🔥', 'STREAK',
 '{"type":"STREAK","days":7}',
 200),

('STREAK_30',
 '30-Day Legend',
 'Maintain a 30-day task completion streak. Incredible consistency!',
 '🏆', 'STREAK',
 '{"type":"STREAK","days":30}',
 500),

('STREAK_100',
 'Century Crusher',
 'A 100-day streak. You are an elite level human.',
 '💎', 'STREAK',
 '{"type":"STREAK","days":100}',
 2000),

('STREAK_365',
 'Year of Quests',
 'An entire year of unbroken daily productivity. Legendary!',
 '👑', 'STREAK',
 '{"type":"STREAK","days":365}',
 10000),

-- ── XP_MILESTONE badges ────────────────────────────────────────────────────
('XP_100',
 'XP Initiate',
 'Earn your first 100 XP.',
 '⭐', 'XP_MILESTONE',
 '{"type":"XP","threshold":100}',
 0),

('XP_1000',
 'XP Adventurer',
 'Accumulate 1,000 total XP.',
 '⭐⭐', 'XP_MILESTONE',
 '{"type":"XP","threshold":1000}',
 100),

('XP_10000',
 'XP Master',
 'Accumulate 10,000 total XP.',
 '⭐⭐⭐', 'XP_MILESTONE',
 '{"type":"XP","threshold":10000}',
 500),

('XP_100000',
 'XP Grandmaster',
 'Accumulate 100,000 total XP. You are in a league of your own.',
 '🌟', 'XP_MILESTONE',
 '{"type":"XP","threshold":100000}',
 5000),

-- ── TASK_COUNT badges ──────────────────────────────────────────────────────
('FIRST_TASK',
 'Quest Begins',
 'Complete your very first task. The journey of a thousand miles starts here.',
 '✅', 'TASK_COUNT',
 '{"type":"TASK_COUNT","count":1}',
 0),

('TASK_MASTER_10',
 'Task Starter',
 'Complete 10 tasks.',
 '📋', 'TASK_COUNT',
 '{"type":"TASK_COUNT","count":10}',
 50),

('TASK_MASTER_50',
 'Task Warrior',
 'Complete 50 tasks.',
 '⚔️', 'TASK_COUNT',
 '{"type":"TASK_COUNT","count":50}',
 200),

('TASK_MASTER_100',
 'Century Tasker',
 'Complete 100 tasks. You are a productivity machine!',
 '🤖', 'TASK_COUNT',
 '{"type":"TASK_COUNT","count":100}',
 500),

-- ── SPECIAL badges ─────────────────────────────────────────────────────────
('LEVEL_5',
 'Rising Hero',
 'Reach Level 5.',
 '🦸', 'SPECIAL',
 '{"type":"LEVEL","level":5}',
 100),

('LEVEL_10',
 'Seasoned Quester',
 'Reach Level 10.',
 '🗡️', 'SPECIAL',
 '{"type":"LEVEL","level":10}',
 300),

('LEVEL_25',
 'Elite Champion',
 'Reach Level 25. An elite among questers.',
 '🏅', 'SPECIAL',
 '{"type":"LEVEL","level":25}',
 1000),

('EARLY_BIRD',
 'Early Bird',
 'Complete a task before 8:00 AM. Rise and grind!',
 '🌅', 'SPECIAL',
 '{"type":"TIME","before":"08:00"}',
 75);

