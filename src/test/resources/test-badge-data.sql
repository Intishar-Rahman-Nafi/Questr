-- test-badge-data.sql
-- Mirrors V6__seed_badges.sql — used in @DataJpaTest via @Sql annotation.
-- Executed BEFORE_TEST_CLASS so data persists across all rolled-back tests.

INSERT INTO badges (id, name, display_name, description, icon_url, badge_type, criteria, reward_xp) VALUES
  (gen_random_uuid(), 'STREAK_1',   'First Flame',      'Complete a task on your first day.',                    '🔥',     'STREAK',        '{"type":"STREAK","days":1}',           0),
  (gen_random_uuid(), 'STREAK_3',   '3-Day Warrior',    'Maintain a 3-day task completion streak.',              '🔥🔥',   'STREAK',        '{"type":"STREAK","days":3}',          50),
  (gen_random_uuid(), 'STREAK_7',   '7-Day Champion',   'Maintain a 7-day task completion streak.',             '🔥🔥🔥', 'STREAK',        '{"type":"STREAK","days":7}',         200),
  (gen_random_uuid(), 'STREAK_30',  '30-Day Legend',    'Maintain a 30-day task completion streak.',            '🏆',     'STREAK',        '{"type":"STREAK","days":30}',        500),
  (gen_random_uuid(), 'STREAK_100', 'Century Crusher',  'A 100-day streak.',                                    '💎',     'STREAK',        '{"type":"STREAK","days":100}',      2000),
  (gen_random_uuid(), 'STREAK_365', 'Year of Quests',   'An entire year of unbroken daily productivity.',       '👑',     'STREAK',        '{"type":"STREAK","days":365}',     10000),
  (gen_random_uuid(), 'XP_100',     'XP Initiate',      'Earn your first 100 XP.',                              '⭐',     'XP_MILESTONE',  '{"type":"XP","threshold":100}',        0),
  (gen_random_uuid(), 'XP_1000',    'XP Adventurer',    'Accumulate 1,000 total XP.',                           '⭐⭐',   'XP_MILESTONE',  '{"type":"XP","threshold":1000}',     100),
  (gen_random_uuid(), 'XP_10000',   'XP Master',        'Accumulate 10,000 total XP.',                          '⭐⭐⭐', 'XP_MILESTONE',  '{"type":"XP","threshold":10000}',    500),
  (gen_random_uuid(), 'XP_100000',  'XP Grandmaster',   'Accumulate 100,000 total XP.',                         '🌟',     'XP_MILESTONE',  '{"type":"XP","threshold":100000}',  5000),
  (gen_random_uuid(), 'FIRST_TASK', 'Quest Begins',     'Complete your very first task.',                        '✅',     'TASK_COUNT',    '{"type":"TASK_COUNT","count":1}',      0),
  (gen_random_uuid(), 'TASK_MASTER_10',  'Task Starter', 'Complete 10 tasks.',                                  '📋',     'TASK_COUNT',    '{"type":"TASK_COUNT","count":10}',    50),
  (gen_random_uuid(), 'TASK_MASTER_50',  'Task Warrior', 'Complete 50 tasks.',                                  '⚔️',     'TASK_COUNT',    '{"type":"TASK_COUNT","count":50}',   200),
  (gen_random_uuid(), 'TASK_MASTER_100', 'Century Tasker','Complete 100 tasks.',                                '🤖',     'TASK_COUNT',    '{"type":"TASK_COUNT","count":100}',  500),
  (gen_random_uuid(), 'LEVEL_5',    'Rising Hero',      'Reach Level 5.',                                        '🦸',     'SPECIAL',       '{"type":"LEVEL","level":5}',         100),
  (gen_random_uuid(), 'LEVEL_10',   'Seasoned Quester', 'Reach Level 10.',                                       '🗡️',    'SPECIAL',       '{"type":"LEVEL","level":10}',        300),
  (gen_random_uuid(), 'LEVEL_25',   'Elite Champion',   'Reach Level 25.',                                       '🏅',     'SPECIAL',       '{"type":"LEVEL","level":25}',       1000),
  (gen_random_uuid(), 'EARLY_BIRD', 'Early Bird',       'Complete a task before 8:00 AM.',                       '🌅',     'SPECIAL',       '{"type":"TIME","before":"08:00"}',    75);

