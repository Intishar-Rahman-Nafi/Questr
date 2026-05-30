import { useState, forwardRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Plus, Check, Trash2, Zap, Calendar, Filter, X, ChevronDown } from 'lucide-react'
import { toast } from 'sonner'
import confetti from 'canvas-confetti'
import { tasksApi } from '@/api'
import { queryKeys } from '@/lib/queryKeys'
import { formatRelative } from '@/lib/formatDate'
import { cn } from '@/lib/cn'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import type { Task, TaskCategory, TaskPriority } from '@/shared/types/api.types'

// ── Constants ──────────────────────────────────────────────────────────────
const CATEGORIES: TaskCategory[] = ['WORK','PERSONAL','HEALTH','LEARNING','DEV','OTHER']
const PRIORITIES: TaskPriority[]  = ['LOW','MEDIUM','HIGH']
const PRIORITY_LABELS: Record<TaskPriority, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' }
const PRIORITY_COLORS: Record<TaskPriority, string> = {
  LOW: 'text-slate-400', MEDIUM: 'text-amber-400', HIGH: 'text-orange-400',
}

// ── Task Card ──────────────────────────────────────────────────────────────
// forwardRef is required because AnimatePresence mode="popLayout" needs to
// attach a ref to each exiting child to measure its layout before removal.
const TaskCard = forwardRef<HTMLDivElement, {
  task: Task
  onComplete: (id: string) => void
  onDelete:   (id: string) => void
}>(function TaskCard({ task, onComplete, onDelete }, ref) {
  return (
    <motion.div
      ref={ref}
      layout
      initial={{ opacity: 0, y: 8, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, x: -20, scale: 0.95 }}
      transition={{ duration: 0.2 }}
      className={cn(
        'card p-4 flex items-start gap-3 group transition-all duration-200',
        task.completed ? 'opacity-60' : 'hover:border-white/10',
      )}
    >
      {/* Complete button */}
      <button
        onClick={() => !task.completed && onComplete(task.id)}
        disabled={task.completed}
        className={cn(
          'flex-shrink-0 w-6 h-6 rounded-full border-2 flex items-center justify-center transition-all duration-200 mt-0.5',
          task.completed
            ? 'bg-green-500 border-green-500'
            : 'border-white/20 hover:border-brand-400 hover:shadow-glow-brand-sm',
        )}
      >
        {task.completed && <Check className="w-3 h-3 text-white" strokeWidth={3} />}
      </button>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <p className={cn('text-sm font-medium text-white leading-snug', task.completed && 'line-through text-slate-500')}>
            {task.title}
          </p>
          <button
            onClick={() => onDelete(task.id)}
            className="flex-shrink-0 opacity-0 group-hover:opacity-100 text-slate-600 hover:text-red-400 transition-all p-1 -mt-1 -mr-1 rounded-lg hover:bg-red-500/10"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>

        {task.description && (
          <p className="text-xs text-slate-500 mt-1 leading-relaxed line-clamp-2">{task.description}</p>
        )}

        <div className="flex flex-wrap items-center gap-2 mt-2">
          <span className={cn('text-[11px] px-2 py-0.5 rounded-full font-medium', `cat-${task.category}`)}>
            {task.category}
          </span>
          <span className={cn('text-[11px] font-medium', PRIORITY_COLORS[task.priority])}>
            {PRIORITY_LABELS[task.priority]}
          </span>
          <span className="flex items-center gap-1 text-[11px] text-amber-400 font-medium">
            <Zap className="w-3 h-3" />{task.xpReward} XP
          </span>
          {task.dueDate && (
            <span className="flex items-center gap-1 text-[11px] text-slate-500">
              <Calendar className="w-3 h-3" />{formatRelative(task.dueDate)}
            </span>
          )}
        </div>
      </div>
    </motion.div>
  )
})

// ── Add Task Form ──────────────────────────────────────────────────────────
const taskSchema = z.object({
  title:       z.string().min(1, 'Title is required').max(120),
  description: z.string().max(500).optional(),
  category:    z.enum(['WORK','PERSONAL','HEALTH','LEARNING','DEV','OTHER'] as const),
  priority:    z.enum(['LOW','MEDIUM','HIGH'] as const),
  xpReward:    z.number().int().min(1).max(1000).optional(),
  dueDate:     z.string().optional(),
})
type TaskForm = z.infer<typeof taskSchema>

function AddTaskModal({ open, onClose, onSave }: { open: boolean; onClose: () => void; onSave: (d: TaskForm) => Promise<void> }) {
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<TaskForm>({
    resolver: zodResolver(taskSchema),
    defaultValues: { category: 'PERSONAL', priority: 'MEDIUM', xpReward: 10 },
  })

  const submit = async (d: TaskForm) => {
    await onSave(d)
    reset()
    onClose()
  }

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" onClick={onClose} />
          {/* Centering wrapper — Framer Motion y animation overrides Tailwind -translate-y-1/2 so use flex wrapper */}
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
                <h2 className="text-lg font-semibold text-white">New Quest</h2>
                <button onClick={onClose} className="text-slate-500 hover:text-white transition-colors p-1 rounded-lg hover:bg-white/5">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleSubmit(submit)} className="space-y-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-400">Quest Title *</label>
                  <input {...register('title')} className="input-field" placeholder="What needs to be done?" autoFocus />
                  {errors.title && <p className="text-xs text-red-400">{errors.title.message}</p>}
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-400">Description</label>
                  <textarea {...register('description')} className="input-field resize-none h-20"
                    placeholder="Optional details..." />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-slate-400">Category</label>
                    <div className="relative">
                      <select {...register('category')} className="input-field appearance-none pr-8 cursor-pointer">
                        {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                      <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500 pointer-events-none" />
                    </div>
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-slate-400">Priority</label>
                    <div className="relative">
                      <select {...register('priority')} className="input-field appearance-none pr-8 cursor-pointer">
                        {PRIORITIES.map(p => <option key={p} value={p}>{PRIORITY_LABELS[p]}</option>)}
                      </select>
                      <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500 pointer-events-none" />
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-slate-400 flex items-center gap-1">
                      <Zap className="w-3 h-3 text-amber-400" />XP Reward
                    </label>
                    <input {...register('xpReward', { valueAsNumber: true })} type="number" min={1} max={1000}
                      className="input-field" placeholder="10" />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-slate-400 flex items-center gap-1">
                      <Calendar className="w-3 h-3" />Due Date
                    </label>
                    <input {...register('dueDate')} type="date" className="input-field" />
                  </div>
                </div>

                <div className="flex gap-3 pt-1">
                  <button type="button" onClick={onClose}
                    className="flex-1 py-2.5 rounded-xl text-sm font-medium text-slate-400 hover:text-white bg-white/5 hover:bg-white/8 transition-all">
                    Cancel
                  </button>
                  <button type="submit" disabled={isSubmitting} className="btn-brand flex-1 justify-center py-2.5">
                    {isSubmitting ? <Spinner size="sm" /> : <><Plus className="w-4 h-4" /> Add Quest</>}
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

// ── Page ───────────────────────────────────────────────────────────────────
type FilterTab = 'all' | 'active' | 'done'

export function TasksPage() {
  const qc = useQueryClient()
  const [modal,      setModal]     = useState(false)
  const [tab,        setTab]       = useState<FilterTab>('active')
  const [catFilter,  setCatFilter] = useState<TaskCategory | 'all'>('all')
  const [priFilter,  setPriFilter] = useState<TaskPriority | 'all'>('all')

  const { data: rawTasks, isLoading } = useQuery({
    queryKey: queryKeys.tasks(),
    queryFn:  () => tasksApi.list(),
  })
  const tasks: Task[] = Array.isArray(rawTasks) ? rawTasks : []

  const createMut = useMutation({
    mutationFn: tasksApi.create,
    onSuccess: () => { qc.invalidateQueries({ queryKey: queryKeys.tasks() }); qc.invalidateQueries({ queryKey: queryKeys.dashboard }) },
  })

  const completeMut = useMutation({
    mutationFn: (id: string) => tasksApi.complete(id),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: queryKeys.tasks() })
      qc.invalidateQueries({ queryKey: queryKeys.dashboard })
      toast.success(`+${data.xpReward} XP earned! Quest complete 🎉`)
      confetti({ particleCount: 80, spread: 60, origin: { y: 0.6 }, colors: ['#7c3aed','#06b6d4','#22c55e'] })
    },
    onError: () => toast.error('Failed to complete task'),
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => tasksApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: queryKeys.tasks() }); toast.info('Quest removed') },
  })

  const filtered = tasks
    .filter(t => tab === 'all' ? true : tab === 'active' ? !t.completed : t.completed)
    .filter(t => catFilter === 'all' ? true : t.category === catFilter)
    .filter(t => priFilter === 'all' ? true : t.priority === priFilter)

  const activeCount = tasks.filter(t => !t.completed).length
  const doneCount   = tasks.filter(t =>  t.completed).length

  return (
    <div className="page-wrapper space-y-5">
      {/* Header */}
      <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}
        className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">Quests</h1>
          <p className="text-slate-500 text-sm mt-0.5">{activeCount} active · {doneCount} completed</p>
        </div>
        <button onClick={() => setModal(true)} className="btn-brand">
          <Plus className="w-4 h-4" /><span className="hidden sm:inline">New Quest</span>
        </button>
      </motion.div>

      {/* Tabs */}
      <div className="flex items-center gap-1 p-1 rounded-xl bg-white/5 w-fit">
        {(['all','active','done'] as FilterTab[]).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={cn('px-4 py-1.5 text-sm font-medium rounded-lg transition-all duration-200 capitalize',
              tab === t ? 'bg-white/10 text-white' : 'text-slate-500 hover:text-white')}>
            {t} {t === 'active' ? `(${activeCount})` : t === 'done' ? `(${doneCount})` : `(${tasks.length})`}
          </button>
        ))}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2">
        <Filter className="w-4 h-4 text-slate-500 flex-shrink-0" />
        <div className="relative">
          <select value={catFilter} onChange={e => setCatFilter(e.target.value as any)}
            className="text-xs bg-white/5 border border-white/10 rounded-lg px-3 py-1.5 text-slate-300 cursor-pointer outline-none focus:border-brand-500/50 appearance-none pr-7">
            <option value="all">All categories</option>
            {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
          <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3 h-3 text-slate-500 pointer-events-none" />
        </div>
        <div className="relative">
          <select value={priFilter} onChange={e => setPriFilter(e.target.value as any)}
            className="text-xs bg-white/5 border border-white/10 rounded-lg px-3 py-1.5 text-slate-300 cursor-pointer outline-none focus:border-brand-500/50 appearance-none pr-7">
            <option value="all">All priorities</option>
            {PRIORITIES.map(p => <option key={p} value={p}>{PRIORITY_LABELS[p]}</option>)}
          </select>
          <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3 h-3 text-slate-500 pointer-events-none" />
        </div>
        {(catFilter !== 'all' || priFilter !== 'all') && (
          <button onClick={() => { setCatFilter('all'); setPriFilter('all') }}
            className="text-xs text-slate-500 hover:text-white flex items-center gap-1 transition-colors">
            <X className="w-3 h-3" />Clear
          </button>
        )}
      </div>

      {/* Task list */}
      {isLoading ? (
        <div className="space-y-3">
          {[...Array(4)].map((_,i) => <div key={i} className="h-20 skeleton" />)}
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={tab === 'done' ? '🏆' : '⚔️'}
          title={tab === 'done' ? 'No completed quests yet' : 'No active quests'}
          body={tab === 'done' ? 'Complete some quests to see them here.' : 'Create your first quest and start earning XP!'}
          action={tab !== 'done' ? (
            <button onClick={() => setModal(true)} className="btn-brand">
              <Plus className="w-4 h-4" />Add first quest
            </button>
          ) : undefined}
        />
      ) : (
        <motion.div className="space-y-3" layout>
          <AnimatePresence mode="popLayout">
            {filtered.map(task => (
              <TaskCard
                key={task.id}
                task={task}
                onComplete={id => completeMut.mutate(id)}
                onDelete={id => deleteMut.mutate(id)}
              />
            ))}
          </AnimatePresence>
        </motion.div>
      )}

      <AddTaskModal
        open={modal}
        onClose={() => setModal(false)}
        onSave={async (d) => { await createMut.mutateAsync(d); toast.success('Quest added! 🗡️') }}
      />
    </div>
  )
}

