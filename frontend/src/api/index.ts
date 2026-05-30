import { api } from './axios'
import type {
  AuthResponse, LoginRequest, SignupRequest,
  Task, CreateTaskRequest, UpdateTaskRequest,
  Badge, DashboardResponse, WeeklyHistory,
  Challenge, ChallengeStatus, CreateChallengeRequest,
  AIReportResponse,
} from '@/shared/types/api.types'

// ── Auth ───────────────────────────────────────────────────────────────────
export const authApi = {
  login:   (data: LoginRequest)  => api.post<AuthResponse>('/auth/login',  data).then(r => r.data),
  signup:  (data: SignupRequest) => api.post<AuthResponse>('/auth/signup', data).then(r => r.data),
  refresh: (refreshToken: string) =>
    api.post<AuthResponse>('/auth/refresh', { refreshToken }).then(r => r.data),
}

// ── Task field adapter ─────────────────────────────────────────────────────
// Backend uses: xpValue, deadline   Frontend uses: xpReward, dueDate
function adaptTask(raw: any): Task {
  return {
    id:          raw.id,
    userId:      raw.userId ?? '',
    title:       raw.title,
    description: raw.description ?? null,
    category:    raw.category,
    priority:    raw.priority,
    xpReward:    raw.xpReward ?? raw.xpValue ?? 0,
    completed:   raw.completed ?? false,
    completedAt: raw.completedAt ?? null,
    dueDate:     raw.dueDate ?? raw.deadline ?? null,
    createdAt:   raw.createdAt,
  }
}

function toBackendCreate(data: CreateTaskRequest) {
  return {
    title:       data.title,
    description: data.description ?? null,
    category:    data.category,
    priority:    data.priority,
    // Convert date string "YYYY-MM-DD" → "YYYY-MM-DDTHH:mm:ss" for LocalDateTime
    // Only send if not empty
    deadline: data.dueDate ? `${data.dueDate}T23:59:00` : null,
  }
}

// ── Tasks ──────────────────────────────────────────────────────────────────
export const tasksApi = {
  list: (params?: { category?: string; priority?: string; completed?: boolean }) =>
    api.get<any>('/tasks', { params }).then(r => {
      // Backend returns Page<TaskResponse> — extract content array
      const raw = r.data
      const items: any[] = Array.isArray(raw) ? raw : (raw?.content ?? [])
      return items.map(adaptTask)
    }),

  get: (id: string) =>
    api.get<any>(`/tasks/${id}`).then(r => adaptTask(r.data)),

  create: (data: CreateTaskRequest) =>
    api.post<any>('/tasks', toBackendCreate(data)).then(r => adaptTask(r.data)),

  update: (id: string, d: UpdateTaskRequest) =>
    api.put<any>(`/tasks/${id}`, toBackendCreate(d as CreateTaskRequest)).then(r => adaptTask(r.data)),

  delete: (id: string) => api.delete(`/tasks/${id}`),

  complete: (id: string) =>
    api.patch<any>(`/tasks/${id}/complete`).then(r => adaptTask(r.data)),
}

// ── Badge field adapter ────────────────────────────────────────────────────
// Backend fields: iconUrl, badgeType, displayName
// Frontend fields: icon, criteriaType, name (use displayName if present)
// Backend response shape: { earnedCount, totalCount, earned: [...], locked: [...] }
function adaptBadge(raw: any): Badge {
  return {
    id:           raw.id,
    name:         raw.displayName ?? raw.name,
    description:  raw.description,
    icon:         raw.iconUrl ?? raw.icon ?? '🏅',
    criteriaType: raw.badgeType ?? raw.criteriaType ?? '',
    rewardXp:     raw.rewardXp ?? 0,
    earnedAt:     raw.earnedAt ?? null,
  }
}

// ── Achievements ───────────────────────────────────────────────────────────
export const achievementsApi = {
  // Backend returns AchievementsResponse { earnedCount, totalCount, earned[], locked[] }
  // Merge earned + locked into a single flat array for the UI
  list: (): Promise<Badge[]> =>
    api.get<any>('/achievements').then(r => {
      const data = r.data
      const earned: any[] = Array.isArray(data?.earned) ? data.earned : []
      const locked: any[] = Array.isArray(data?.locked) ? data.locked : []
      return [...earned, ...locked].map(adaptBadge)
    }),
}

// ── Dashboard ──────────────────────────────────────────────────────────────
// Backend DashboardResponse fields:
//   weeklyCompletions: [{dayOfWeek, date, count}]  (7 days, current week)
//   categoryBreakdown: [{category, count, percentage}]
// Frontend DashboardResponse expects:
//   activityGrid: [{date, tasksCompleted, xpEarned}]
function adaptDashboard(raw: any) {
  const weekly: any[] = Array.isArray(raw?.weeklyCompletions) ? raw.weeklyCompletions : []
  return {
    ...raw,
    // Map weeklyCompletions → activityGrid format
    activityGrid: weekly.map((d: any) => ({
      date:           d.date ?? '',
      tasksCompleted: d.count ?? 0,
      xpEarned:       0,  // backend doesn't provide per-day XP in this endpoint
    })),
  }
}

export const dashboardApi = {
  get:     () => api.get<any>('/dashboard').then(r => adaptDashboard(r.data)),
  history: (weeks = 8) =>
    api.get<WeeklyHistory[]>('/dashboard/history', { params: { weeks } }).then(r => r.data),
}

// ── Challenge field adapter ────────────────────────────────────────────────
// Backend ChallengeResponse: { id, name, description, inviteCode, startDate,
//   endDate, targetXp, createdById, createdByUsername, createdAt,
//   participantCount, active, creator }
// Frontend Challenge: { id, title, description, inviteCode, startDate,
//   endDate, targetXp, creatorId, creatorUsername, status,
//   participantCount, participants, myCurrentXp, myRank, joined }
function adaptChallenge(raw: any, leaderboardEntries?: any[]): Challenge {
  const now   = Date.now()
  const start = new Date(raw.startDate).getTime()
  const end   = new Date(raw.endDate).getTime()

  let status: Challenge['status']
  if (raw.status) {
    status = raw.status
  } else if (raw.active === true || (start <= now && end > now)) {
    status = 'ACTIVE'
  } else if (start > now) {
    status = 'UPCOMING'
  } else {
    status = 'COMPLETED'
  }

  const participants: Challenge['participants'] = (leaderboardEntries ?? []).map((e: any) => ({
    userId:    e.userId,
    username:  e.username,
    currentXp: e.currentXp,
    rank:      e.rank,
    joinedAt:  e.joinedAt,
  }))

  return {
    id:               raw.id,
    title:            raw.title ?? raw.name ?? '',
    description:      raw.description ?? '',
    targetXp:         raw.targetXp ?? 0,
    startDate:        raw.startDate,
    endDate:          raw.endDate,
    creatorId:        raw.creatorId ?? raw.createdById ?? '',
    creatorUsername:  raw.creatorUsername ?? raw.createdByUsername ?? '',
    status,
    participantCount: raw.participantCount ?? 0,
    participants,
    inviteCode:       raw.inviteCode ?? '',
    myCurrentXp:      raw.myCurrentXp,
    myRank:           raw.myRank,
    joined:           raw.joined,
  }
}

// Maps frontend CreateChallengeRequest (uses 'title') → backend (uses 'name')
function toBackendChallenge(data: CreateChallengeRequest) {
  return {
    name:        data.title,
    description: data.description ?? null,
    targetXp:    data.targetXp,
    startDate:   data.startDate ? `${data.startDate}T00:00:00` : null,
    endDate:     data.endDate   ? `${data.endDate}T23:59:59`   : null,
  }
}

// ── Challenges ─────────────────────────────────────────────────────────────
export const challengesApi = {
  // Backend: GET /challenges?filter=all  → all user challenges
  list: () =>
    api.get<any[]>('/challenges', { params: { filter: 'all' } })
      .then(r => (Array.isArray(r.data) ? r.data : []).map(c => adaptChallenge(c))),

  // Backend: GET /challenges?filter=active  → active user challenges
  my: () =>
    api.get<any[]>('/challenges', { params: { filter: 'active' } })
      .then(r => (Array.isArray(r.data) ? r.data : []).map(c => adaptChallenge(c))),

  // Backend: GET /challenges/{id} (basic info) + GET /challenges/{id}/leaderboard (participants)
  get: async (id: string): Promise<Challenge> => {
    const [challengeRes, leaderboardRes] = await Promise.allSettled([
      api.get<any>(`/challenges/${id}`).then(r => r.data),
      api.get<any>(`/challenges/${id}/leaderboard`).then(r => r.data),
    ])
    if (challengeRes.status === 'rejected') {
      throw new Error('Challenge not found')
    }
    const c   = challengeRes.value
    const lb  = leaderboardRes.status === 'fulfilled' ? leaderboardRes.value : null
    return adaptChallenge(c, lb?.entries ?? [])
  },

  // Backend: POST /challenges  { name, description, targetXp, startDate, endDate }
  create: (data: CreateChallengeRequest) =>
    api.post<any>('/challenges', toBackendChallenge(data)).then(r => adaptChallenge(r.data)),

  // Backend: POST /challenges/join  { inviteCode }  (inviteCode = 6-char code, NOT uuid)
  join: (inviteCode: string) =>
    api.post<any>('/challenges/join', { inviteCode }).then(r => adaptChallenge(r.data)),

  leave: (id: string) => api.post(`/challenges/${id}/leave`),
}

// ── AI Report ──────────────────────────────────────────────────────────────
export const reportApi = {
  get:        () => api.get<AIReportResponse>('/reports/weekly').then(r => r.data),
  regenerate: () => api.post<AIReportResponse>('/reports/weekly/regenerate').then(r => r.data),
}

