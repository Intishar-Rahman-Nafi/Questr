import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  ArrowLeft, Users, Zap, Calendar, Trophy, Crown,
  Medal, LogOut, UserPlus, Clock, Copy, Check, Trash2,
} from 'lucide-react'
import { toast } from 'sonner'
import { challengesApi } from '@/api'
import { queryKeys } from '@/lib/queryKeys'
import { formatDate } from '@/lib/formatDate'
import { cn } from '@/lib/cn'
import { StatCard } from '@/components/ui/StatCard'
import { Spinner } from '@/components/ui/Spinner'
import { useAuthStore } from '@/store/authStore'
import type { ChallengeParticipant } from '@/shared/types/api.types'

function RankIcon({ rank }: { rank: number }) {
  if (rank === 1) return <Crown className="w-4 h-4 text-amber-400" />
  if (rank === 2) return <Medal className="w-4 h-4 text-slate-300" />
  if (rank === 3) return <Medal className="w-4 h-4 text-amber-700" />
  return <span className="text-xs text-slate-500 font-mono w-4 text-center">{rank}</span>
}

function ParticipantRow({
  p, targetXp, isMe, index,
}: { p: ChallengeParticipant; targetXp: number; isMe: boolean; index: number }) {
  const pct = Math.min(100, Math.round((p.currentXp / targetXp) * 100))
  return (
    <motion.div
      initial={{ opacity: 0, x: -8 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: index * 0.04 }}
      className={cn(
        'flex items-center gap-4 p-4 rounded-xl transition-all duration-200',
        isMe ? 'bg-brand-600/15 border border-brand-600/30' : 'bg-white/[0.03] hover:bg-white/5',
      )}
    >
      <div className="flex-shrink-0 w-8 flex items-center justify-center">
        <RankIcon rank={p.rank} />
      </div>
      <div className={cn(
        'w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0',
        isMe ? 'bg-gradient-brand text-white' : 'bg-white/10 text-slate-300',
      )}>
        {(p.username ?? '??').slice(0, 2).toUpperCase()}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <span className={cn('text-sm font-medium', isMe ? 'text-white' : 'text-slate-300')}>
            {p.username}
          </span>
          {isMe && (
            <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-brand-500/20 text-brand-400">You</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <div className="xp-bar flex-1 h-1.5">
            <motion.div className="xp-fill h-full" initial={{ width: 0 }}
              animate={{ width: `${pct}%` }} transition={{ duration: 0.7, delay: index * 0.04 + 0.1 }} />
          </div>
          <span className="text-[11px] text-slate-500 flex-shrink-0 w-16 text-right">
            {p.currentXp.toLocaleString()} XP
          </span>
        </div>
      </div>
      <span className={cn('text-xs font-semibold flex-shrink-0 w-10 text-right', pct >= 100 ? 'text-green-400' : 'text-slate-500')}>
        {pct}%
      </span>
    </motion.div>
  )
}

// ── Invite code copy panel (for creator only) ──────────────────────────────
function InviteCodePanel({ code }: { code: string }) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}
      className="card p-4 flex items-center justify-between gap-4">
      <div>
        <p className="text-xs text-slate-500 mb-0.5">Invite Code — share with others to let them join</p>
        <span className="text-xl font-mono font-bold tracking-[0.2em] text-brand-400">{code}</span>
      </div>
      <button onClick={copy}
        className="flex items-center gap-2 px-3 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-slate-400 hover:text-white transition-all text-xs font-medium">
        {copied ? <><Check className="w-3.5 h-3.5 text-green-400" />Copied!</> : <><Copy className="w-3.5 h-3.5" />Copy</>}
      </button>
    </motion.div>
  )
}

const STATUS_STYLES: Record<string, string> = {
  UPCOMING:  'bg-blue-500/15 text-blue-400',
  ACTIVE:    'bg-green-500/15 text-green-400',
  COMPLETED: 'bg-slate-500/15 text-slate-400',
  CANCELLED: 'bg-red-500/15 text-red-400',
}

export function ChallengeDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const userId = useAuthStore(s => s.userId)

  const { data: challenge, isLoading } = useQuery({
    queryKey: queryKeys.challenge(id!),
    queryFn:  () => challengesApi.get(id!),
    enabled:  !!id,
  })

  const joinMut = useMutation({
    mutationFn: (inviteCode: string) => challengesApi.join(inviteCode),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.challenge(id!) })
      qc.invalidateQueries({ queryKey: queryKeys.challenges() })
      qc.invalidateQueries({ queryKey: queryKeys.myChallenges })
      toast.success('Joined the challenge! 🏆')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message ?? 'Failed to join'),
  })

  const leaveMut = useMutation({
    mutationFn: () => challengesApi.leave(id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.challenge(id!) })
      qc.invalidateQueries({ queryKey: queryKeys.challenges() })
      qc.invalidateQueries({ queryKey: queryKeys.myChallenges })
      toast.info('Left the challenge')
      navigate('/challenges')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message ?? 'Failed to leave'),
  })

  const deleteMut = useMutation({
    mutationFn: () => challengesApi.delete(id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.challenges() })
      qc.invalidateQueries({ queryKey: queryKeys.myChallenges })
      toast.success('Challenge deleted')
      navigate('/challenges')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message ?? 'Failed to delete'),
  })

  if (isLoading) return (
    <div className="page-wrapper space-y-6 animate-fade-in">
      <div className="h-8 w-32 skeleton" />
      <div className="h-28 skeleton" />
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[...Array(4)].map((_,i) => <div key={i} className="h-24 skeleton" />)}
      </div>
      <div className="h-64 skeleton" />
    </div>
  )

  if (!challenge) return (
    <div className="page-wrapper flex flex-col items-center justify-center py-20 gap-4">
      <div className="text-5xl">🗺️</div>
      <h2 className="text-xl font-bold text-white">Challenge not found</h2>
      <Link to="/challenges" className="btn-brand"><ArrowLeft className="w-4 h-4" />Back</Link>
    </div>
  )

  // Use the explicit joined/creator flags from the backend
  const joined      = challenge.joined ?? false
  const isCreator   = challenge.creator ?? false
  const sortedPart  = [...(challenge.participants ?? [])].sort((a, b) => a.rank - b.rank)
  const myProgress  = challenge.myCurrentXp ?? 0
  const progressPct = Math.min(100, Math.round((myProgress / challenge.targetXp) * 100))
  const daysLeft    = Math.max(0, Math.ceil((new Date(challenge.endDate).getTime() - Date.now()) / 86_400_000))

  return (
    <div className="page-wrapper space-y-6">
      <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}>
        <button onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-slate-400 hover:text-white transition-colors text-sm mb-4">
          <ArrowLeft className="w-4 h-4" /> Back to Challenges
        </button>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-2 mb-2">
              <span className={cn('text-[11px] font-medium px-2 py-0.5 rounded-full', STATUS_STYLES[challenge.status])}>
                {challenge.status}
              </span>
              {isCreator && (
                <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-400">You created this</span>
              )}
              <span className="text-[11px] text-slate-500">by {challenge.creatorUsername}</span>
            </div>
            <h1 className="text-2xl font-bold text-white">{challenge.title}</h1>
            <p className="text-slate-400 text-sm mt-2 max-w-2xl">{challenge.description}</p>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            {challenge.status === 'ACTIVE' && (
              <>
                {isCreator ? (
                  // Creator cannot leave — they can only delete
                  <button onClick={() => {
                    if (confirm('Delete this challenge? This cannot be undone.')) deleteMut.mutate()
                  }} disabled={deleteMut.isPending}
                    className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-red-400 hover:text-red-300 bg-red-500/10 hover:bg-red-500/15 border border-red-500/20 transition-all">
                    {deleteMut.isPending ? <Spinner size="sm" /> : <Trash2 className="w-4 h-4" />}Delete
                  </button>
                ) : joined ? (
                  <button onClick={() => leaveMut.mutate()} disabled={leaveMut.isPending}
                    className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-red-400 hover:text-red-300 bg-red-500/10 hover:bg-red-500/15 border border-red-500/20 transition-all">
                    {leaveMut.isPending ? <Spinner size="sm" /> : <LogOut className="w-4 h-4" />}Leave
                  </button>
                ) : (
                  <button onClick={() => joinMut.mutate(challenge.inviteCode)} disabled={joinMut.isPending} className="btn-brand">
                    {joinMut.isPending ? <Spinner size="sm" /> : <UserPlus className="w-4 h-4" />}Join Challenge
                  </button>
                )}
              </>
            )}
          </div>
        </div>
      </motion.div>

      {/* Invite code panel — only visible to creator */}
      {isCreator && challenge.inviteCode && (
        <InviteCodePanel code={challenge.inviteCode} />
      )}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard index={0} icon={<Zap className="w-5 h-5" />}      label="XP Target"    value={challenge.targetXp.toLocaleString()} color="amber" />
        <StatCard index={1} icon={<Users className="w-5 h-5" />}    label="Participants" value={challenge.participantCount}          color="cyan" />
        <StatCard index={2} icon={<Calendar className="w-5 h-5" />} label="Ends"         value={formatDate(challenge.endDate)}       color="brand" />
        <StatCard index={3} icon={<Clock className="w-5 h-5" />}    label="Days Left"    value={challenge.status === 'ACTIVE' ? `${daysLeft}d` : '—'} color="green" />
      </div>

      {joined && challenge.status === 'ACTIVE' && (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
          className="card p-5 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-white flex items-center gap-2">
              <Trophy className="w-4 h-4 text-brand-400" />Your Progress
            </h3>
            <span className="text-xs text-slate-500">Rank #{challenge.myRank ?? '—'}</span>
          </div>
          <div className="flex items-center justify-between text-xs mb-1">
            <span className="text-slate-500">{myProgress.toLocaleString()} / {challenge.targetXp.toLocaleString()} XP</span>
            <span className={cn('font-semibold', progressPct >= 100 ? 'text-green-400' : 'text-brand-400')}>{progressPct}%</span>
          </div>
          <div className="xp-bar">
            <motion.div className="xp-fill" initial={{ width: 0 }}
              animate={{ width: `${progressPct}%` }} transition={{ duration: 0.8 }} />
          </div>
        </motion.div>
      )}

      <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}
        className="card p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-white flex items-center gap-2">
            <Crown className="w-4 h-4 text-amber-400" />Leaderboard
          </h3>
          <span className="text-xs text-slate-500">{sortedPart.length} participants</span>
        </div>
        {sortedPart.length === 0 ? (
          <div className="py-10 text-center text-slate-500 text-sm">
            {joined
              ? "You're the first one here — share your invite code to grow the leaderboard!"
              : 'No participants yet. Join the challenge to see the leaderboard!'}
          </div>
        ) : (
          <div className="space-y-2">
            {sortedPart.map((p, i) => (
              <ParticipantRow key={p.userId} p={p} targetXp={challenge.targetXp} isMe={p.userId === userId} index={i} />
            ))}
          </div>
        )}
      </motion.div>

      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 }}
        className="card p-4 flex flex-wrap gap-4 text-sm text-slate-500 items-center">
        <span className="flex items-center gap-2">
          <Calendar className="w-4 h-4" />
          Started: <span className="text-slate-300">{formatDate(challenge.startDate)}</span>
        </span>
        <span className="text-white/10">|</span>
        <span className="flex items-center gap-2">
          <Calendar className="w-4 h-4" />
          Ends: <span className="text-slate-300">{formatDate(challenge.endDate)}</span>
        </span>
      </motion.div>
    </div>
  )
}

