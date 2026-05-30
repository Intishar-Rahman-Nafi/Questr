import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link } from 'react-router-dom'
import { Plus, Users, Zap, Calendar, ArrowRight, X, Trophy } from 'lucide-react'
import { toast } from 'sonner'
import { challengesApi } from '@/api'
import { queryKeys } from '@/lib/queryKeys'
import { formatDate } from '@/lib/formatDate'
import { cn } from '@/lib/cn'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { useAuthStore } from '@/store/authStore'
import type { Challenge, ChallengeStatus } from '@/shared/types/api.types'

const STATUS_STYLES: Record<ChallengeStatus, string> = {
  UPCOMING:  'bg-blue-500/15 text-blue-400',
  ACTIVE:    'bg-green-500/15 text-green-400',
  COMPLETED: 'bg-slate-500/15 text-slate-400',
  CANCELLED: 'bg-red-500/15 text-red-400',
}

function ChallengeCard({ challenge, onJoin, isJoining, myUserId }: {
  challenge: Challenge
  onJoin:    (id: string) => void
  isJoining: boolean
  myUserId:  string | null
}) {
  const joined    = (challenge.participants ?? []).some(p => p.userId === myUserId)
  const progress  = Math.min(100, Math.round(((challenge.myCurrentXp ?? 0) / challenge.targetXp) * 100))
  const daysLeft  = Math.max(0, Math.ceil((new Date(challenge.endDate).getTime() - Date.now()) / 86_400_000))

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="card p-5 flex flex-col gap-4 group hover:border-white/10 transition-all duration-200"
    >
      {/* Top row */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className={cn('text-[11px] font-medium px-2 py-0.5 rounded-full', STATUS_STYLES[challenge.status])}>
              {challenge.status}
            </span>
            {joined && (
              <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-brand-500/15 text-brand-400">Joined</span>
            )}
          </div>
          <h3 className="text-base font-semibold text-white leading-snug">{challenge.title}</h3>
          <p className="text-xs text-slate-500 mt-1 line-clamp-2">{challenge.description}</p>
        </div>
      </div>

      {/* Meta row */}
      <div className="flex flex-wrap gap-3 text-xs text-slate-500">
        <span className="flex items-center gap-1">
          <Zap className="w-3 h-3 text-amber-400" />
          <span className="text-amber-400 font-medium">{challenge.targetXp.toLocaleString()}</span> XP target
        </span>
        <span className="flex items-center gap-1">
          <Users className="w-3 h-3" />{challenge.participantCount} adventurers
        </span>
        <span className="flex items-center gap-1">
          <Calendar className="w-3 h-3" />
          {challenge.status === 'ACTIVE' ? `${daysLeft}d left` : formatDate(challenge.startDate)}
        </span>
      </div>

      {/* Progress (if joined) */}
      {joined && challenge.status === 'ACTIVE' && (
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-xs">
            <span className="text-slate-500">Your progress</span>
            <span className="text-brand-400 font-medium">{challenge.myCurrentXp ?? 0} / {challenge.targetXp} XP</span>
          </div>
          <div className="xp-bar">
            <motion.div className="xp-fill" initial={{ width: 0 }}
              animate={{ width: `${progress}%` }} transition={{ duration: 0.7 }} />
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="flex items-center gap-2 mt-auto pt-1">
        <Link to={`/challenges/${challenge.id}`}
          className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors">
          View details <ArrowRight className="w-3 h-3" />
        </Link>
        <div className="flex-1" />
        {!joined && challenge.status === 'ACTIVE' && (
          <button onClick={() => onJoin(challenge.id)} disabled={isJoining}
            className="btn-brand text-xs py-1.5 px-3">
            {isJoining ? <Spinner size="sm" /> : <><Trophy className="w-3 h-3" />Join</>}
          </button>
        )}
      </div>
    </motion.div>
  )
}

// ── Create Challenge Modal ─────────────────────────────────────────────────
const schema = z.object({
  title:       z.string().min(3, 'Min 3 chars').max(80),
  description: z.string().min(10, 'Min 10 chars').max(500),
  targetXp:    z.number().int().min(50).max(100_000),
  startDate:   z.string().min(1, 'Required'),
  endDate:     z.string().min(1, 'Required'),
})
type CForm = z.infer<typeof schema>

function CreateModal({ open, onClose, onCreate }: {
  open: boolean; onClose: () => void; onCreate: (d: CForm) => Promise<void>
}) {
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<CForm>({
    resolver: zodResolver(schema),
    defaultValues: { targetXp: 500 },
  })

  const submit = async (d: CForm) => { await onCreate(d); reset(); onClose() }

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" onClick={onClose} />
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
            <motion.div
              initial={{ opacity: 0, scale: 0.94, y: 16 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.94, y: 16 }}
              transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
              className="w-full max-w-md pointer-events-auto"
            >
              <div className="card p-6">
              <div className="flex items-center justify-between mb-5">
                <h2 className="text-lg font-semibold text-white">Create Challenge</h2>
                <button onClick={onClose} className="text-slate-500 hover:text-white transition-colors">
                  <X className="w-5 h-5" />
                </button>
              </div>
              <form onSubmit={handleSubmit(submit)} className="space-y-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-400">Title *</label>
                  <input {...register('title')} className="input-field" placeholder="100-Day Sprint" autoFocus />
                  {errors.title && <p className="text-xs text-red-400">{errors.title.message}</p>}
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-400">Description *</label>
                  <textarea {...register('description')} className="input-field resize-none h-20" placeholder="What's the challenge about?" />
                  {errors.description && <p className="text-xs text-red-400">{errors.description.message}</p>}
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-400 flex items-center gap-1">
                    <Zap className="w-3 h-3 text-amber-400" />XP Target
                  </label>
                  <input {...register('targetXp', { valueAsNumber: true })} type="number" min={50} max={100000}
                    className="input-field" placeholder="500" />
                  {errors.targetXp && <p className="text-xs text-red-400">{errors.targetXp.message}</p>}
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-slate-400">Start Date</label>
                    <input {...register('startDate')} type="date" className="input-field" />
                    {errors.startDate && <p className="text-xs text-red-400">{errors.startDate.message}</p>}
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-slate-400">End Date</label>
                    <input {...register('endDate')} type="date" className="input-field" />
                    {errors.endDate && <p className="text-xs text-red-400">{errors.endDate.message}</p>}
                  </div>
                </div>
                <div className="flex gap-3 pt-1">
                  <button type="button" onClick={onClose}
                    className="flex-1 py-2.5 rounded-xl text-sm font-medium text-slate-400 bg-white/5 hover:bg-white/8 transition-all">
                    Cancel
                  </button>
                  <button type="submit" disabled={isSubmitting} className="btn-brand flex-1 justify-center py-2.5">
                    {isSubmitting ? <Spinner size="sm" /> : <><Plus className="w-4 h-4" />Create</>}
                  </button>
                </div>
              </form>
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  )
}

export function ChallengesPage() {
  const qc = useQueryClient()
  const [modal, setModal]   = useState(false)
  const [tab,   setTab]     = useState<'all' | 'my'>('all')
  const [joiningId, setJoiningId] = useState<string | null>(null)
  const userId = useAuthStore(s => s.userId)

  const { data: rawAll, isLoading: allLoading } = useQuery({ queryKey: queryKeys.challenges(), queryFn: challengesApi.list })
  const { data: rawMy,  isLoading: myLoading  } = useQuery({ queryKey: queryKeys.myChallenges,  queryFn: challengesApi.my })
  const all: Challenge[] = Array.isArray(rawAll) ? rawAll : []
  const my:  Challenge[] = Array.isArray(rawMy)  ? rawMy  : []

  const createMut = useMutation({
    mutationFn: challengesApi.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.challenges() })
      qc.invalidateQueries({ queryKey: queryKeys.myChallenges })
      toast.success('Challenge created! ⚔️')
    },
  })

  const joinMut = useMutation({
    mutationFn: (id: string) => challengesApi.join(id),
    onSettled: () => setJoiningId(null),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.challenges() })
      qc.invalidateQueries({ queryKey: queryKeys.myChallenges })
      toast.success('Joined the challenge! 🏆')
    },
  })

  const handleJoin = (id: string) => { setJoiningId(id); joinMut.mutate(id) }

  const challenges = tab === 'my' ? my : all
  const isLoading  = tab === 'my' ? myLoading : allLoading

  return (
    <div className="page-wrapper space-y-5">
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}
        className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">Challenges</h1>
          <p className="text-slate-500 text-sm mt-0.5">Compete with others, climb the leaderboard</p>
        </div>
        <button onClick={() => setModal(true)} className="btn-brand">
          <Plus className="w-4 h-4" /><span className="hidden sm:inline">Create</span>
        </button>
      </motion.div>

      {/* Tabs */}
      <div className="flex gap-1 p-1 rounded-xl bg-white/5 w-fit">
        {(['all','my'] as const).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={cn('px-5 py-1.5 text-sm font-medium rounded-lg transition-all duration-200',
              tab === t ? 'bg-white/10 text-white' : 'text-slate-500 hover:text-white')}>
            {t === 'all' ? `Browse (${all.length})` : `My Challenges (${my.length})`}
          </button>
        ))}
      </div>

      {/* Grid */}
      {isLoading ? (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(6)].map((_,i) => <div key={i} className="h-48 skeleton" />)}
        </div>
      ) : challenges.length === 0 ? (
        <EmptyState
          icon="⚔️"
          title={tab === 'my' ? "You haven't joined any challenges" : "No challenges yet"}
          body={tab === 'my' ? 'Browse challenges and join one!' : 'Be the first to create a challenge!'}
          action={<button onClick={() => setModal(true)} className="btn-brand"><Plus className="w-4 h-4" />Create challenge</button>}
        />
      ) : (
        <motion.div layout className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <AnimatePresence>
            {challenges.map(c => (
              <ChallengeCard key={c.id} challenge={c} myUserId={userId}
                onJoin={handleJoin} isJoining={joiningId === c.id} />
            ))}
          </AnimatePresence>
        </motion.div>
      )}

      <CreateModal open={modal} onClose={() => setModal(false)} onCreate={async d => { await createMut.mutateAsync(d) }} />
    </div>
  )
}

