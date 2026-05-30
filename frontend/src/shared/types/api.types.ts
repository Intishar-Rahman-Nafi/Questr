// ── Auth ───────────────────────────────────────────────────────────────────
export interface AuthResponse {
  accessToken:  string
  refreshToken: string
  userId:       string
  username:     string
  email:        string
}

export interface LoginRequest    { usernameOrEmail: string; password: string }
export interface SignupRequest   { username: string; email: string; password: string }
export interface RefreshRequest  { refreshToken: string }

// ── Task ───────────────────────────────────────────────────────────────────
// Backend TaskCategory enum: WORK, PERSONAL, HEALTH, LEARNING, DEV, OTHER
export type TaskCategory = 'WORK' | 'PERSONAL' | 'HEALTH' | 'LEARNING' | 'DEV' | 'OTHER'
// Backend TaskPriority enum: LOW, MEDIUM, HIGH
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Task {
  id:          string
  userId:      string
  title:       string
  description: string | null
  category:    TaskCategory
  priority:    TaskPriority
  xpReward:    number
  completed:   boolean
  completedAt: string | null
  dueDate:     string | null
  createdAt:   string
}

export interface CreateTaskRequest {
  title:       string
  description?: string
  category:    TaskCategory
  priority:    TaskPriority
  xpReward?:   number
  dueDate?:    string
}

export interface UpdateTaskRequest extends Partial<CreateTaskRequest> {}

// ── Achievement / Badge ─────────────────────────────────────────────────────
export interface Badge {
  id:            string
  name:          string
  description:   string
  icon:          string
  criteriaType:  string
  rewardXp:      number
  earnedAt:      string | null
  progressHint?: string | null   // only on locked badges
}

// ── UserStats ──────────────────────────────────────────────────────────────
export interface UserStats {
  userId:           string
  totalXp:          number
  level:            number
  currentStreak:    number
  longestStreak:    number
  tasksCompleted:   number
  lastActivityDate: string | null
}

// ── Dashboard ──────────────────────────────────────────────────────────────
export interface ActivityDay {
  date:           string
  tasksCompleted: number
  xpEarned:       number
}

export interface CategoryBreakdown {
  category:   TaskCategory
  count:      number
  percentage: number
}

export interface DashboardResponse {
  level:              number
  totalXp:            number
  xpToNextLevel:      number
  currentStreak:      number
  longestStreak:      number
  tasksCompleted:     number
  activityGrid:       ActivityDay[]
  categoryBreakdown:  CategoryBreakdown[]
}

export interface WeeklyHistory {
  weekNumber:            number
  year:                  number
  weekStart:             string
  weekEnd:               string
  totalXp:               number
  tasksCompleted:        number
  averageCompletionRate: number
}

// ── Challenge ──────────────────────────────────────────────────────────────
export type ChallengeStatus = 'UPCOMING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

export interface ChallengeParticipant {
  userId:    string
  username:  string
  currentXp: number
  rank:      number
  joinedAt:  string
}

export interface Challenge {
  id:               string
  title:            string
  description:      string
  targetXp:         number
  startDate:        string
  endDate:          string
  creatorId:        string
  creatorUsername:  string
  status:           ChallengeStatus
  participantCount: number
  participants:     ChallengeParticipant[]
  inviteCode:       string
  myCurrentXp?:     number
  myRank?:          number
  joined?:          boolean
}

export interface CreateChallengeRequest {
  title:       string
  description: string
  targetXp:    number
  startDate:   string
  endDate:     string
}

// ── AI Report ──────────────────────────────────────────────────────────────
export interface AIReportResponse {
  summary:      string
  tips:         string[]
  improvements: string
  quote:        string
  weekStart:    string
  weekEnd:      string
  generatedAt:  string
  fallback:     boolean
}

// ── Generic ────────────────────────────────────────────────────────────────
export interface ApiError {
  message:   string
  status:    number
  timestamp: string
}

export interface PageResponse<T> {
  content:       T[]
  totalElements: number
  totalPages:    number
  size:          number
  number:        number
}

