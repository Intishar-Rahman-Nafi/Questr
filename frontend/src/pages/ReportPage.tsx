import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { Brain, RefreshCw, Lightbulb, TrendingUp, Quote, Calendar, Sparkles, AlertTriangle, Clock } from 'lucide-react'
import { toast } from 'sonner'
import { reportApi } from '@/api'
import { queryKeys } from '@/lib/queryKeys'
import { formatDate } from '@/lib/formatDate'
import { cn } from '@/lib/cn'
import { Spinner } from '@/components/ui/Spinner'

// ── Tip card ──────────────────────────────────────────────────────────────
function TipCard({ tip, index }: { tip: string; index: number }) {
  return (
    <motion.div
      initial={{ opacity: 0, x: -8 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: 0.3 + index * 0.06 }}
      className="flex items-start gap-3 p-4 rounded-xl bg-white/[0.03] border border-white/5 hover:border-white/10 transition-colors"
    >
      <div className="w-6 h-6 rounded-full bg-brand-500/20 text-brand-400 flex items-center justify-center text-xs font-bold flex-shrink-0 mt-0.5">
        {index + 1}
      </div>
      <p className="text-sm text-slate-300 leading-relaxed">{tip}</p>
    </motion.div>
  )
}

// ── Skeleton ──────────────────────────────────────────────────────────────
function ReportSkeleton() {
  return (
    <div className="page-wrapper space-y-6 animate-fade-in">
      <div className="h-8 w-48 skeleton" />
      <div className="h-40 skeleton" />
      <div className="h-8 w-32 skeleton" />
      <div className="space-y-3">
        {[...Array(4)].map((_,i) => <div key={i} className="h-16 skeleton" />)}
      </div>
      <div className="h-24 skeleton" />
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────
export function ReportPage() {
  const qc = useQueryClient()
  // Track rate-limit state so button becomes disabled after a 429
  const [rateLimited, setRateLimited] = useState(false)

  const { data: report, isLoading, isError } = useQuery({
    queryKey: queryKeys.report,
    queryFn:  reportApi.get,
    retry: 1,
  })

  const regenMut = useMutation({
    mutationFn: reportApi.regenerate,
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.report, data)
      setRateLimited(false)
      toast.success('AI Report regenerated! ✨')
    },
    onError: (err: any) => {
      const status = err?.response?.status
      if (status === 429) {
        const msg: string =
          err?.response?.data?.message ??
          'You have reached the daily regeneration limit (3 per day). Try again tomorrow.'
        setRateLimited(true)
        toast.error(msg, { duration: 6000 })
      } else {
        toast.error('Could not regenerate report. Try again later.')
      }
    },
  })

  if (isLoading) return <ReportSkeleton />

  if (isError || !report) return (
    <div className="page-wrapper flex flex-col items-center justify-center py-20 gap-5 text-center">
      <div className="w-16 h-16 rounded-2xl bg-amber-500/10 flex items-center justify-center">
        <AlertTriangle className="w-8 h-8 text-amber-400" />
      </div>
      <h2 className="text-xl font-bold text-white">Report unavailable</h2>
      <p className="text-slate-400 text-sm max-w-sm">
        Complete some tasks this week to generate your first AI-powered weekly report!
      </p>
      <button onClick={() => regenMut.mutate()} disabled={regenMut.isPending || rateLimited} className="btn-brand">
        {regenMut.isPending ? <Spinner size="sm" /> : <Brain className="w-4 h-4" />}
        {rateLimited ? 'Daily limit reached' : 'Generate Report'}
      </button>
    </div>
  )

  return (
    <div className="page-wrapper space-y-6">
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}
        className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2">
            <Brain className="w-6 h-6 text-brand-400" />
            AI Weekly Report
          </h1>
          <div className="flex items-center gap-2 mt-1 text-sm text-slate-500">
            <Calendar className="w-3.5 h-3.5" />
            <span>{formatDate(report.weekStart)} – {formatDate(report.weekEnd)}</span>
            {report.fallback && (
              <span className="text-[11px] px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-400 font-medium">
                Fallback mode
              </span>
            )}
          </div>
        </div>

        <button
          onClick={() => regenMut.mutate()}
          disabled={regenMut.isPending || rateLimited}
          title={rateLimited ? 'Daily regeneration limit reached. Try again tomorrow.' : undefined}
          className={cn(
            'flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all border',
            rateLimited
              ? 'text-slate-500 bg-white/3 border-white/5 cursor-not-allowed'
              : 'text-slate-300 hover:text-white bg-white/5 hover:bg-white/8 border-white/10 hover:border-white/20',
          )}
        >
          {regenMut.isPending
            ? <><Spinner size="sm" /><span>Generating…</span></>
            : rateLimited
              ? <><Clock className="w-4 h-4" /><span>Limit reached</span></>
              : <><RefreshCw className="w-4 h-4" /><span>Regenerate</span></>
          }
        </button>
      </motion.div>

      {/* Summary card */}
      <motion.div
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
        className="card p-6 relative overflow-hidden"
      >
        {/* Background glow */}
        <div className="absolute inset-0 opacity-[0.03] pointer-events-none"
          style={{ background: 'radial-gradient(ellipse at 0% 0%, #7c3aed, transparent 60%)' }} />

        <div className="flex items-center gap-2 mb-4">
          <div className="w-8 h-8 rounded-lg bg-gradient-brand flex items-center justify-center flex-shrink-0">
            <Sparkles className="w-4 h-4 text-white" />
          </div>
          <h2 className="text-sm font-semibold text-white">Weekly Summary</h2>
        </div>

        <p className="text-slate-300 text-sm leading-relaxed">{report.summary}</p>

        <div className="mt-4 pt-4 border-t border-white/5 flex items-center gap-1.5 text-xs text-slate-600">
          <Brain className="w-3 h-3" />
          Generated {formatDate(report.generatedAt)}
        </div>
      </motion.div>

      {/* Tips */}
      {report.tips.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
          className="space-y-3"
        >
          <h2 className="text-sm font-semibold text-white flex items-center gap-2">
            <Lightbulb className="w-4 h-4 text-amber-400" />
            Actionable Tips
          </h2>
          <div className="space-y-2">
            <AnimatePresence>
              {report.tips.map((tip, i) => (
                <TipCard key={i} tip={tip} index={i} />
              ))}
            </AnimatePresence>
          </div>
        </motion.div>
      )}

      {/* Improvements */}
      {report.improvements && (
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.35 }}
          className="card p-5 space-y-3"
        >
          <h2 className="text-sm font-semibold text-white flex items-center gap-2">
            <TrendingUp className="w-4 h-4 text-cyan-400" />
            Areas to Improve
          </h2>
          <p className="text-sm text-slate-300 leading-relaxed">{report.improvements}</p>
        </motion.div>
      )}

      {/* Motivational quote */}
      {report.quote && (
        <motion.div
          initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.45 }}
          className="card p-6 relative overflow-hidden"
        >
          <div className="absolute inset-0 opacity-[0.04] pointer-events-none"
            style={{ background: 'radial-gradient(ellipse at 100% 100%, #06b6d4, transparent 60%)' }} />
          <div className="flex gap-4">
            <Quote className="w-6 h-6 text-cyan-500/50 flex-shrink-0 mt-1" />
            <blockquote className="text-base text-slate-200 italic leading-relaxed font-light">
              {report.quote}
            </blockquote>
          </div>
        </motion.div>
      )}
    </div>
  )
}

