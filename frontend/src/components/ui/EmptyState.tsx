import { type ReactNode } from 'react'
interface EmptyStateProps {
  icon:    string
  title:   string
  body:    string
  action?: ReactNode
}
export function EmptyState({ icon, title, body, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-20 gap-4 text-center">
      <div className="text-5xl animate-float">{icon}</div>
      <h3 className="text-lg font-semibold text-white">{title}</h3>
      <p className="text-sm text-slate-500 max-w-xs">{body}</p>
      {action}
    </div>
  )
}
