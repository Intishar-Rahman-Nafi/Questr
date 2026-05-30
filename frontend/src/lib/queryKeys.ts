export const queryKeys = {
  dashboard:         ['dashboard']            as const,
  dashboardHistory:  (weeks: number) => ['dashboard', 'history', weeks] as const,
  tasks:             (filters?: object) => ['tasks', filters ?? {}]      as const,
  task:              (id: string) => ['tasks', id]                       as const,
  achievements:      ['achievements']         as const,
  challenges:        (page?: number) => ['challenges', page ?? 0]        as const,
  challenge:         (id: string) => ['challenges', id]                  as const,
  myChallenges:      ['challenges', 'my']     as const,
  report:            ['report', 'weekly']     as const,
} as const

