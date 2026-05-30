import { useQuery } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { useState } from 'react'
import { Lock, Zap, X, Calendar } from 'lucide-react'
import { achievementsApi } from '@/api'
import { queryKeys } from '@/lib/queryKeys'
import { formatDate } from '@/lib/formatDate'
import { cn } from '@/lib/cn'
import { EmptyState } from '@/components/ui/EmptyState'
import type { Badge } from '@/shared/types/api.types'

function BadgeCard({ badge, onClick }: { badge: Badge; onClick: () => void }) {
  const earned = !!badge.earnedAt
  return (
    <motion.button
      layout
      initial={{ opacity: 0, scale: 0.92 }}
      animate={{ opacity: 1, scale: 1 }}
      whileHover={{ scale: 1.04, y: -2 }}
      whileTap={{ scale: 0.97 }}
      onClick={onClick}
      className={cn(
        'card p-4 text-center flex flex-col items-center gap-3 transition-all duration-200 w-full',
        earned
          ? 'border-brand-600/30 hover:border-brand-500/50'
          : 'opacity-50 hover:opacity-70',
      )}
      style={earned ? { boxShadow: '0 4px 24px rgba(0,0,0,0.5), 0 0 12px rgba(124,58,237,0.15)' } : {}}
    >
      {/* Icon */}
      <div className={cn(
        'w-14 h-14 rounded-2xl flex items-center justify-center text-2xl relative',
        earned ? 'bg-gradient-brand shadow-glow-brand' : 'bg-white/5',
      )}>
        {earned ? badge.icon : <Lock className="w-5 h-5 text-slate-600" />}
        {earned && (
          <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-green-500 border-2 border-bg-base" />
        )}
      </div>

      <div className="space-y-1 min-w-0 w-full">
        <p className={cn('text-sm font-semibold leading-tight', earned ? 'text-white' : 'text-slate-500')}>
          {badge.name}
        </p>
        <p className="text-[11px] text-slate-600 line-clamp-2">{badge.description}</p>
        <div className="flex items-center justify-center gap-1 mt-1">
          <Zap className="w-3 h-3 text-amber-400" />
          <span className="text-[11px] text-amber-400 font-medium">{badge.rewardXp} XP</span>
        </div>
      </div>
    </motion.button>
  )
}

function BadgeModal({ badge, onClose }: { badge: Badge | null; onClose: () => void }) {
  return (
    <AnimatePresence>
      {badge && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" onClick={onClose} />
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
            <motion.div
              initial={{ opacity: 0, scale: 0.9, y: 16 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.9, y: 16 }}
              transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
              className="w-full max-w-sm pointer-events-auto"
            >
              <div className="card p-6 text-center relative">
              <button onClick={onClose} className="absolute top-4 right-4 text-slate-500 hover:text-white transition-colors">
                <X className="w-5 h-5" />
              </button>

              <div className={cn(
                'w-20 h-20 rounded-3xl flex items-center justify-center text-4xl mx-auto mb-4',
                badge.earnedAt ? 'bg-gradient-brand shadow-glow-brand' : 'bg-white/5',
              )}>
                {badge.earnedAt ? badge.icon : <Lock className="w-8 h-8 text-slate-600" />}
              </div>

              <h3 className="text-xl font-bold text-white mb-1">{badge.name}</h3>
              <p className="text-sm text-slate-400 mb-4">{badge.description}</p>

              <div className="flex items-center justify-center gap-3 mb-4">
                <div className="flex items-center gap-1.5 bg-amber-500/10 text-amber-400 px-3 py-1.5 rounded-full text-xs font-medium">
                  <Zap className="w-3 h-3" />{badge.rewardXp} XP Reward
                </div>
                <div className="text-xs text-slate-500 bg-white/5 px-3 py-1.5 rounded-full">
                  {badge.criteriaType.replace(/_/g, ' ')}
                </div>
              </div>

              {badge.earnedAt ? (
                <div className="flex items-center justify-center gap-2 text-green-400 text-sm font-medium">
                  <Calendar className="w-4 h-4" />
                  Earned {formatDate(badge.earnedAt)}
                </div>
              ) : (
                <p className="text-sm text-slate-500">
                  {badge.progressHint ?? 'Keep going to unlock this badge!'}
                </p>
              )}
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  )
}

export function AchievementsPage() {
  const [selected, setSelected] = useState<Badge | null>(null)
  const [filter,   setFilter]   = useState<'all' | 'earned' | 'locked'>('all')

  const { data: rawBadges, isLoading } = useQuery({
    queryKey: queryKeys.achievements,
    queryFn:  achievementsApi.list,
  })
  const badges: Badge[] = Array.isArray(rawBadges) ? rawBadges : []

  const earned = badges.filter(b =>  b.earnedAt)
  const locked = badges.filter(b => !b.earnedAt)
  const filtered = filter === 'earned' ? earned : filter === 'locked' ? locked : badges

  return (
    <div className="page-wrapper space-y-6">
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-2xl font-bold text-white">Achievements</h1>
        <p className="text-slate-500 text-sm mt-1">
          <span className="text-brand-400 font-medium">{earned.length}</span> earned · {locked.length} locked
        </p>
      </motion.div>

      {/* Progress bar */}
      {badges.length > 0 && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.1 }}
          className="card p-4 space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-white">Trophy Collection</span>
            <span className="text-xs text-slate-500">{earned.length}/{badges.length}</span>
          </div>
          <div className="xp-bar">
            <motion.div className="xp-fill" initial={{ width: 0 }}
              animate={{ width: `${(earned.length / badges.length) * 100}%` }}
              transition={{ duration: 0.8, ease: [0.34, 1.56, 0.64, 1] }} />
          </div>
        </motion.div>
      )}

      {/* Filter tabs */}
      <div className="flex gap-1 p-1 rounded-xl bg-white/5 w-fit">
        {(['all','earned','locked'] as const).map(f => (
          <button key={f} onClick={() => setFilter(f)}
            className={cn('px-4 py-1.5 text-sm font-medium rounded-lg transition-all duration-200 capitalize',
              filter === f ? 'bg-white/10 text-white' : 'text-slate-500 hover:text-white')}>
            {f === 'earned' ? `Earned (${earned.length})` : f === 'locked' ? `Locked (${locked.length})` : `All (${badges.length})`}
          </button>
        ))}
      </div>

      {/* Grid */}
      {isLoading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
          {[...Array(10)].map((_,i) => <div key={i} className="h-40 skeleton" />)}
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState icon="🏆" title="No badges here" body="Check back after completing some quests!" />
      ) : (
        <motion.div layout className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
          <AnimatePresence>
            {filtered.map(badge => (
              <BadgeCard key={badge.id} badge={badge} onClick={() => setSelected(badge)} />
            ))}
          </AnimatePresence>
        </motion.div>
      )}

      <BadgeModal badge={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

