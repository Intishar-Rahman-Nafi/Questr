import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Zap, Flame, CheckCircle2, TrendingUp, Star } from 'lucide-react'
import { AreaChart, Area, ResponsiveContainer, XAxis, YAxis, Tooltip, Cell, PieChart, Pie } from 'recharts'
import { dashboardApi } from '@/api'
import { queryKeys } from '@/lib/queryKeys'
import { xpProgressPercent } from '@/lib/level'
import { formatShort } from '@/lib/formatDate'
import { cn } from '@/lib/cn'
import { StatCard } from '@/components/ui/StatCard'
import { XPBar } from '@/components/ui/XPBar'
import { useAuthStore } from '@/store/authStore'
import type { ActivityDay, CategoryBreakdown } from '@/shared/types/api.types'

// ── Activity Heatmap ──────────────────────────────────────────────────────
function ActivityHeatmap({ days }: { days: ActivityDay[] }) {
  const safeDays = days ?? []
  const max = Math.max(...safeDays.map(d => d.tasksCompleted), 1)

  const getColor = (count: number) => {
    if (count === 0) return 'rgba(255,255,255,0.04)'
    const pct = count / max
    if (pct < 0.25) return 'rgba(124,58,237,0.3)'
    if (pct < 0.5)  return 'rgba(124,58,237,0.5)'
    if (pct < 0.75) return 'rgba(124,58,237,0.75)'
    return '#7c3aed'
  }

  // Show last 84 days (12 weeks × 7), fill empty leading days
  const last84 = Array.from({ length: 84 }, (_, i) => {
    const idx = safeDays.length - 84 + i
    return idx >= 0 ? safeDays[idx] : null
  })

  return (
    <div className="space-y-3">
      <div className="flex gap-1 flex-wrap">
        {last84.map((day, i) => (
          <div
            key={i}
            title={day ? `${day.date}: ${day.tasksCompleted} tasks, ${day.xpEarned} XP` : ''}
            className="w-3 h-3 rounded-sm transition-all duration-200 hover:scale-125"
            style={{ backgroundColor: day ? getColor(day.tasksCompleted) : 'rgba(255,255,255,0.03)' }}
          />
        ))}
      </div>
      <div className="flex items-center gap-2 text-[11px] text-slate-600">
        <span>Less</span>
        {[0.04, 0.3, 0.5, 0.75, 1].map((o, i) => (
          <div key={i} className="w-3 h-3 rounded-sm"
            style={{ backgroundColor: o < 0.1 ? 'rgba(255,255,255,0.04)' : `rgba(124,58,237,${o})` }} />
        ))}
        <span>More</span>
      </div>
    </div>
  )
}

// ── Category Donut ────────────────────────────────────────────────────────
const CAT_COLORS: Record<string, string> = {
  WORK: '#3b82f6', PERSONAL: '#a855f7', HEALTH: '#22c55e',
  LEARNING: '#f59e0b', DEV: '#06b6d4', OTHER: '#64748b',
}

function CategoryChart({ data }: { data: CategoryBreakdown[] }) {
  if (!data.length) return null
  return (
    <ResponsiveContainer width="100%" height={160}>
      <PieChart>
        <Pie data={data} dataKey="count" nameKey="category" cx="50%" cy="50%"
          innerRadius={45} outerRadius={65} paddingAngle={3} stroke="none">
          {data.map((entry) => (
            <Cell key={entry.category} fill={CAT_COLORS[entry.category] ?? '#64748b'} />
          ))}
        </Pie>
        <Tooltip
          contentStyle={{ background: '#1a1a2e', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', fontSize: '12px' }}
          itemStyle={{ color: '#e2e8f0' }}
          formatter={(val: number, name: string) => [`${val} tasks`, name]}
        />
      </PieChart>
    </ResponsiveContainer>
  )
}

// ── Trend Area Chart ──────────────────────────────────────────────────────
function TrendChart({ days }: { days: ActivityDay[] }) {
  const safeDays = days ?? []
  const last14 = safeDays.slice(-14).map(d => ({ date: formatShort(d.date), xp: d.xpEarned, tasks: d.tasksCompleted }))
  return (
    <ResponsiveContainer width="100%" height={120}>
      <AreaChart data={last14} margin={{ top: 4, right: 4, bottom: 4, left: -20 }}>
        <defs>
          <linearGradient id="xpGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor="#7c3aed" stopOpacity={0.3} />
            <stop offset="95%" stopColor="#7c3aed" stopOpacity={0} />
          </linearGradient>
        </defs>
        <XAxis dataKey="date" tick={{ fill: '#475569', fontSize: 10 }} tickLine={false} axisLine={false} />
        <YAxis tick={{ fill: '#475569', fontSize: 10 }} tickLine={false} axisLine={false} />
        <Tooltip
          contentStyle={{ background: '#1a1a2e', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', fontSize: '12px' }}
          itemStyle={{ color: '#a78bfa' }}
        />
        <Area type="monotone" dataKey="xp" stroke="#7c3aed" strokeWidth={2} fill="url(#xpGrad)" dot={false} />
      </AreaChart>
    </ResponsiveContainer>
  )
}

// ── Dashboard skeleton ──────────────────────────────────────────────────
function DashSkeleton() {
  return (
    <div className="page-wrapper space-y-6 animate-fade-in">
      <div className="h-7 w-56 skeleton" />
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[...Array(4)].map((_,i) => <div key={i} className="h-24 skeleton" />)}
      </div>
      <div className="h-10 skeleton" />
      <div className="grid lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 h-48 skeleton" />
        <div className="h-48 skeleton" />
      </div>
    </div>
  )
}

export function DashboardPage() {
  const username = useAuthStore(s => s.username)
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn:  dashboardApi.get,
    retry: 1,
    staleTime: 30_000,   // keep fresh for 30s — prevents flicker on tab switch
  })

  if (isLoading) return <DashSkeleton />

  // Only show error screen when there is NO data at all (first load failed).
  // If a background refetch fails but we have stale data, keep showing the data.
  if (!data) return (
    <div className="page-wrapper flex flex-col items-center justify-center py-24 gap-5 text-center">
      <div className="text-5xl">🔌</div>
      <h2 className="text-xl font-bold text-white">Couldn't load dashboard</h2>
      <p className="text-slate-400 text-sm max-w-xs">
        Make sure the backend is running on port 8080, then try again.
      </p>
      <button onClick={() => refetch()} className="btn-brand">
        Retry
      </button>
    </div>
  )

  const pct = xpProgressPercent(data.totalXp ?? 0, data.level ?? 1)
  const greeting = new Date().getHours() < 12 ? 'Good morning' : new Date().getHours() < 18 ? 'Good afternoon' : 'Good evening'
  const activityGrid      = data.activityGrid      ?? []
  const categoryBreakdown = data.categoryBreakdown ?? []

  return (
    <div className="page-wrapper space-y-6">
      {/* Background refetch error banner */}
      {isError && (
        <div className="flex items-center justify-between gap-3 px-4 py-2.5 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
          <span>⚠️ Couldn't refresh dashboard — showing last known data</span>
          <button onClick={() => refetch()} className="underline hover:no-underline">Retry</button>
        </div>
      )}
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <h1 className="text-2xl font-bold text-white">
          {greeting}, <span className="text-gradient-brand">{username}</span> ⚔️
        </h1>
        <p className="text-slate-500 text-sm mt-1">Here's how your quest is going today</p>
      </motion.div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard index={0} icon={<Star className="w-5 h-5" />}  label="Level"   value={data.level}          sub="Keep grinding!" color="brand" />
        <StatCard index={1} icon={<Zap className="w-5 h-5" />}   label="Total XP" value={data.totalXp}        sub={`${data.xpToNextLevel} to next`} color="cyan" />
        <StatCard index={2} icon={<Flame className="w-5 h-5" />} label="Streak"   value={`${data.currentStreak}d`} sub={`Best: ${data.longestStreak}d`} color="amber" />
        <StatCard index={3} icon={<CheckCircle2 className="w-5 h-5" />} label="Completed" value={data.tasksCompleted} sub="All time" color="green" />
      </div>

      {/* XP progress */}
      <motion.div
        initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 }}
        className="card p-5"
      >
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm font-medium text-white flex items-center gap-2">
            <TrendingUp className="w-4 h-4 text-brand-400" />
            XP Progress
          </span>
          <span className="text-xs text-slate-500">{pct}% toward Level {data.level + 1}</span>
        </div>
        <XPBar percent={pct} totalXp={data.totalXp} xpToNext={data.xpToNextLevel} level={data.level} showLabels={false} />
      </motion.div>

      {/* Activity grid + trend chart */}
      <div className="grid lg:grid-cols-3 gap-4">
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
          className="lg:col-span-2 card p-5 space-y-4"
        >
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-white">Activity</h3>
            <span className="text-xs text-slate-500">Last 12 weeks</span>
          </div>
          <ActivityHeatmap days={activityGrid} />
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}
          className="card p-5 space-y-4"
        >
          <h3 className="text-sm font-semibold text-white">XP Earned (14d)</h3>
          <TrendChart days={activityGrid} />
        </motion.div>
      </div>

      {/* Category breakdown */}
      {categoryBreakdown.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
          className="card p-5"
        >
          <h3 className="text-sm font-semibold text-white mb-4">Category Breakdown</h3>
          <div className="grid sm:grid-cols-2 gap-4 items-center">
            <CategoryChart data={categoryBreakdown} />
            <div className="space-y-2">
              {categoryBreakdown.map(c => (
                <div key={c.category} className="flex items-center gap-3">
                  <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: CAT_COLORS[c.category] ?? '#64748b' }} />
                  <span className={cn('text-xs px-2 py-0.5 rounded-full font-medium', `cat-${c.category}`)}>{c.category}</span>
                  <div className="flex-1 h-1.5 rounded-full bg-white/5">
                    <div className="h-full rounded-full" style={{ width: `${c.percentage}%`, backgroundColor: CAT_COLORS[c.category] ?? '#64748b' }} />
                  </div>
                  <span className="text-xs text-slate-500 w-8 text-right">{c.count}</span>
                </div>
              ))}
            </div>
          </div>
        </motion.div>
      )}
    </div>
  )
}

