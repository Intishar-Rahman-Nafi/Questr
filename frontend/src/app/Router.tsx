import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { AppLayout } from '@/components/layout/AppLayout'
import { PageLoader } from '@/components/ui/Spinner'

const LoginPage           = lazy(() => import('@/pages/LoginPage').then(m=>({default:m.LoginPage})))
const SignupPage          = lazy(() => import('@/pages/SignupPage').then(m=>({default:m.SignupPage})))
const DashboardPage       = lazy(() => import('@/pages/DashboardPage').then(m=>({default:m.DashboardPage})))
const TasksPage           = lazy(() => import('@/pages/TasksPage').then(m=>({default:m.TasksPage})))
const AchievementsPage    = lazy(() => import('@/pages/AchievementsPage').then(m=>({default:m.AchievementsPage})))
const ChallengesPage      = lazy(() => import('@/pages/ChallengesPage').then(m=>({default:m.ChallengesPage})))
const ChallengeDetailPage = lazy(() => import('@/pages/ChallengeDetailPage').then(m=>({default:m.ChallengeDetailPage})))
const ReportPage          = lazy(() => import('@/pages/ReportPage').then(m=>({default:m.ReportPage})))

function Skeleton() {
  return (
    <div className="page-wrapper space-y-6 animate-fade-in">
      <div className="h-8 w-48 skeleton" />
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[...Array(4)].map((_,i) => <div key={i} className="h-28 skeleton" />)}
      </div>
      <div className="h-64 skeleton" />
    </div>
  )
}

function Guard({ children }: { children: React.ReactNode }) {
  const auth = useAuthStore(s => s.isAuthenticated)
  if (!auth) return <Navigate to="/login" replace />
  return <AppLayout>{children}</AppLayout>
}

function Public({ children }: { children: React.ReactNode }) {
  const auth = useAuthStore(s => s.isAuthenticated)
  if (auth) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}

export function AppRouter() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route path="/login"  element={<Public><LoginPage /></Public>} />
        <Route path="/signup" element={<Public><SignupPage /></Public>} />

        <Route path="/dashboard"      element={<Guard><DashboardPage /></Guard>} />
        <Route path="/tasks"          element={<Guard><TasksPage /></Guard>} />
        <Route path="/achievements"   element={<Guard><AchievementsPage /></Guard>} />
        <Route path="/challenges"     element={<Guard><ChallengesPage /></Guard>} />
        <Route path="/challenges/:id" element={<Guard><ChallengeDetailPage /></Guard>} />
        <Route path="/report"         element={<Guard><ReportPage /></Guard>} />

        <Route path="/"  element={<Navigate to="/dashboard" replace />} />
        <Route path="*"  element={
          <div className="min-h-screen flex flex-col items-center justify-center text-center p-8 gap-4">
            <div className="text-6xl">🗺️</div>
            <h1 className="text-3xl font-bold text-white">404 — Quest Not Found</h1>
            <p className="text-slate-400">This path leads nowhere, adventurer.</p>
            <a href="/dashboard" className="btn-brand mt-2">← Back to Base Camp</a>
          </div>
        } />
      </Routes>
    </Suspense>
  )
}

