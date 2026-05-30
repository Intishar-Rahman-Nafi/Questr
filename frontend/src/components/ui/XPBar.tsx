import { motion } from 'framer-motion'
import { cn } from '@/lib/cn'

interface XPBarProps {
  percent:    number
  totalXp?:   number
  xpToNext?:  number
  level?:     number
  showLabels?: boolean
  className?:  string
}

export function XPBar({ percent, totalXp, xpToNext, level, showLabels = true, className }: XPBarProps) {
  const clamped = Math.min(100, Math.max(0, percent))

  return (
    <div className={cn('space-y-1.5', className)}>
      {showLabels && (level !== undefined || xpToNext !== undefined) && (
        <div className="flex items-center justify-between text-xs text-slate-500">
          {level !== undefined && <span>Level {level}</span>}
          {xpToNext !== undefined && (
            <span>{totalXp?.toLocaleString() ?? 0} XP · {xpToNext.toLocaleString()} to next level</span>
          )}
        </div>
      )}
      <div className="xp-bar">
        <motion.div
          className="xp-fill"
          initial={{ width: 0 }}
          animate={{ width: `${clamped}%` }}
          transition={{ duration: 0.9, ease: [0.34, 1.56, 0.64, 1] }}
        />
      </div>
      {showLabels && (
        <div className="flex justify-end">
          <span className="text-[11px] text-slate-600">{clamped}%</span>
        </div>
      )}
    </div>
  )
}

