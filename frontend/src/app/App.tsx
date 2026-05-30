import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'sonner'
import { AppRouter } from './Router'

// ── Global error boundary ─────────────────────────────────────────────────
class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { error: Error | null }
> {
  state = { error: null }
  static getDerivedStateFromError(error: Error) { return { error } }
  render() {
    if (this.state.error) {
      return (
        <div className="min-h-screen flex flex-col items-center justify-center gap-6 p-8 text-center">
          <div className="text-6xl">⚠️</div>
          <h1 className="text-2xl font-bold text-white">Something went wrong</h1>
          <p className="text-slate-400 text-sm max-w-sm">
            {(this.state.error as Error).message}
          </p>
          <button
            onClick={() => { this.setState({ error: null }); window.location.href = '/dashboard' }}
            className="btn-brand"
          >
            Try again
          </button>
        </div>
      )
    }
    return this.props.children
  }
}

const qc = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime:  30_000,           // data stays fresh for 30s
      gcTime:     10 * 60 * 1000,   // keep data in cache for 10 min (prevents blank on re-navigation)
      refetchOnWindowFocus: true,
    },
    mutations: { retry: 0 },
  },
})

export function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={qc}>
        <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AppRouter />
        <Toaster
          position="top-right"
          richColors
          toastOptions={{
            style: {
              background: '#1a1a2e',
              border: '1px solid rgba(255,255,255,0.1)',
              color: '#e2e8f0',
              borderRadius: '12px',
              fontFamily: 'Inter, sans-serif',
            },
          }}
        />
      </BrowserRouter>
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

