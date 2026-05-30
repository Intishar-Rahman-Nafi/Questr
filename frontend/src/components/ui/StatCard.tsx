import { motion } from 'framer-motion'
import { cn } from '@/lib/cn'
import type { ReactNode } from 'react'

interface StatCardProps {
  icon:       ReactNode
  label:      string
  value:      string | number
  sub?:       string
  color?:     'brand' | 'cyan' | 'amber' | 'green' | 'rose'
  index?:     number
  className?: string
}

const colors = {
  brand: 'bg-brand-500/10 text-brand-400 ring-brand-500/20',
  cyan:  'bg-cyan-500/10  text-cyan-400  ring-cyan-500/20',
  amber: 'bg-amber-500/10 text-amber-400 ring-amber-500/20',
  green: 'bg-green-500/10 text-green-400 ring-green-500/20',
  rose:  'bg-rose-500/10  text-rose-400  ring-rose-500/20',
}

export function StatCard({ icon, label, value, sub, color = 'brand', index = 0, className }: StatCardProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.07, duration: 0.4 }}
      className={cn('card p-5 flex items-start gap-4', className)}
    >
      <div className={cn('w-11 h-11 rounded-xl ring-1 flex items-center justify-center flex-shrink-0', colors[color])}>
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">{label}</p>
        <p className="text-2xl font-bold text-white mt-0.5 leading-none">{typeof value === 'number' ? value.toLocaleString() : value}</p>
        {sub && <p className="text-xs text-slate-500 mt-1">{sub}</p>}
      </div>
    </motion.div>
  )
}

