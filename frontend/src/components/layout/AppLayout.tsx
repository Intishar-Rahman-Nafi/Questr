import { useState, useEffect } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  LayoutDashboard, CheckSquare, Trophy, Swords, Brain,
  Zap, LogOut, ChevronLeft, ChevronRight, Menu, X, User,
} from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { cn } from '@/lib/cn'
import { toast } from 'sonner'
import { dashboardApi, achievementsApi, challengesApi } from '@/api'
import { queryKeys } from '@/lib/queryKeys'

const NAV = [
  { path: '/dashboard',   label: 'Dashboard',    icon: LayoutDashboard },
  { path: '/tasks',       label: 'Tasks',        icon: CheckSquare },
  { path: '/achievements',label: 'Achievements', icon: Trophy },
  { path: '/challenges',  label: 'Challenges',   icon: Swords },
  { path: '/report',      label: 'AI Report',    icon: Brain },
]

function Sidebar({ collapsed, toggle, onClose, mobile = false }:
  { collapsed: boolean; toggle: ()=>void; onClose?: ()=>void; mobile?: boolean }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { username, logout } = useAuthStore()

  const handleLogout = () => {
    logout()
    toast.info('Logged out. See you next quest! 👋')
    navigate('/login')
  }

  return (
    <div className={cn(
      'flex flex-col h-full bg-bg-card border-r border-white/5 transition-all duration-300',
      mobile ? 'w-64' : collapsed ? 'w-[60px]' : 'w-56',
    )}>
      {/* Logo */}
      <div className={cn(
        'flex items-center gap-3 p-4 border-b border-white/5',
        collapsed && !mobile ? 'justify-center' : '',
      )}>
        <div className="w-8 h-8 rounded-lg bg-gradient-brand flex items-center justify-center flex-shrink-0 shadow-glow-brand">
          <Zap className="w-4 h-4 text-white" fill="white" />
        </div>
        {(!collapsed || mobile) && (
          <span className="text-lg font-bold text-gradient-brand">Questr</span>
        )}
        {mobile
          ? <button onClick={onClose} className="ml-auto text-slate-400 hover:text-white"><X className="w-5 h-5" /></button>
          : <button onClick={toggle}  className="ml-auto text-slate-500 hover:text-white transition-colors">
              {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
            </button>
        }
      </div>

      {/* Nav links */}
      <nav className="flex-1 p-2 space-y-0.5">
        {NAV.map(({ path, label, icon: Icon }) => {
          const active = location.pathname.startsWith(path)
          return (
            <Link
              key={path}
              to={path}
              onClick={mobile ? onClose : undefined}
              className={cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 group relative overflow-hidden',
                active ? 'bg-brand-600/20 text-white border border-brand-600/30' : 'text-slate-400 hover:text-white hover:bg-white/5',
                collapsed && !mobile ? 'justify-center px-0' : '',
              )}
              title={collapsed && !mobile ? label : undefined}
            >
              {active && (
                <motion.div layoutId="sidebar-active"
                  className="absolute inset-0 bg-gradient-to-r from-brand-600/20 to-cyan-500/10 rounded-xl" />
              )}
              <Icon className={cn('w-5 h-5 flex-shrink-0 z-10', active ? 'text-brand-400' : '')} />
              {(!collapsed || mobile) && <span className="text-sm font-medium z-10">{label}</span>}
            </Link>
          )
        })}
      </nav>

      {/* User + logout */}
      <div className="p-2 border-t border-white/5 space-y-1">
        {(!collapsed || mobile) && (
          <div className="flex items-center gap-2.5 px-3 py-2 rounded-xl bg-white/5 mb-1">
            <div className="w-7 h-7 rounded-full bg-gradient-brand flex items-center justify-center flex-shrink-0">
              <User className="w-3.5 h-3.5 text-white" />
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium text-white truncate">{username}</p>
              <p className="text-[11px] text-slate-500">Adventurer</p>
            </div>
          </div>
        )}
        <button
          onClick={handleLogout}
          className={cn(
            'flex items-center gap-3 px-3 py-2.5 rounded-xl w-full text-slate-400 hover:text-red-400 hover:bg-red-500/10 transition-all',
            collapsed && !mobile ? 'justify-center px-0' : '',
          )}
        >
          <LogOut className="w-5 h-5 flex-shrink-0" />
          {(!collapsed || mobile) && <span className="text-sm font-medium">Logout</span>}
        </button>
      </div>
    </div>
  )
}

function BottomNav() {
  const location = useLocation()
  return (
    <nav className="fixed bottom-0 inset-x-0 z-40 bg-bg-card/95 backdrop-blur-md border-t border-white/5 lg:hidden safe-area-bottom">
      <div className="flex">
        {NAV.map(({ path, label, icon: Icon }) => {
          const active = location.pathname.startsWith(path)
          return (
            <Link key={path} to={path}
              className={cn('flex-1 flex flex-col items-center gap-1 py-3 transition-colors',
                active ? 'text-brand-400' : 'text-slate-500 hover:text-slate-300',
              )}>
              <Icon className="w-5 h-5" />
              <span className="text-[10px] font-medium leading-none">{label.split(' ')[0]}</span>
            </Link>
          )
        })}
      </div>
    </nav>
  )
}

export function AppLayout({ children }: { children: React.ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)
  const [drawer,    setDrawer]    = useState(false)
  const location = useLocation()
  const qc = useQueryClient()

  // Pre-fetch all primary page data as soon as the user is authenticated.
  // This ensures every page has data in the React Query cache before the user
  // navigates to it, eliminating blank/loading screens during navigation.
  useEffect(() => {
    qc.prefetchQuery({ queryKey: queryKeys.dashboard,    queryFn: dashboardApi.get })
    qc.prefetchQuery({ queryKey: queryKeys.achievements, queryFn: achievementsApi.list })
    qc.prefetchQuery({ queryKey: queryKeys.challenges(), queryFn: challengesApi.list })
    qc.prefetchQuery({ queryKey: queryKeys.myChallenges, queryFn: challengesApi.my })
  }, [qc])

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Desktop sidebar */}
      <div className="hidden lg:flex flex-shrink-0">
        <Sidebar collapsed={collapsed} toggle={() => setCollapsed(!collapsed)} />
      </div>

      {/* Mobile drawer */}
      <AnimatePresence>
        {drawer && <>
          <motion.div initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}}
            className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm lg:hidden"
            onClick={() => setDrawer(false)} />
          <motion.div
            initial={{x:-280}} animate={{x:0}} exit={{x:-280}}
            transition={{ type:'spring', stiffness:300, damping:30 }}
            className="fixed left-0 top-0 bottom-0 z-50 lg:hidden"
          >
            <Sidebar collapsed={false} toggle={()=>{}} onClose={() => setDrawer(false)} mobile />
          </motion.div>
        </>}
      </AnimatePresence>

      {/* Content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Mobile top bar */}
        <header className="lg:hidden flex items-center gap-3 px-4 h-14 bg-bg-card/80 backdrop-blur-md border-b border-white/5 flex-shrink-0">
          <button onClick={() => setDrawer(true)} className="text-slate-400 hover:text-white">
            <Menu className="w-5 h-5" />
          </button>
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-lg bg-gradient-brand flex items-center justify-center">
              <Zap className="w-3 h-3 text-white" fill="white" />
            </div>
            <span className="font-bold text-gradient-brand text-sm">Questr</span>
          </div>
        </header>

        {/* Page */}
        <main className="flex-1 overflow-y-auto pb-20 lg:pb-0">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{   opacity: 0 }}
              transition={{ duration: 0.1 }}
              className="min-h-full"
            >
              {children}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>

      <BottomNav />
    </div>
  )
}

