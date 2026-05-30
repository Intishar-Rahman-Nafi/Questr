import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { motion } from 'framer-motion'
import { Zap, Eye, EyeOff, ArrowRight, Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { authApi } from '@/api'
import { useAuthStore } from '@/store/authStore'
import { Spinner } from '@/components/ui/Spinner'

const schema = z.object({
  usernameOrEmail: z.string().min(1, 'Required'),
  password:        z.string().min(1, 'Required'),
})
type Form = z.infer<typeof schema>

export function LoginPage() {
  const [showPw, setShowPw] = useState(false)
  const navigate = useNavigate()
  const login = useAuthStore(s => s.login)

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({
    resolver: zodResolver(schema),
  })

  const onSubmit = async (data: Form) => {
    try {
      const res = await authApi.login(data)
      login(res)
      toast.success('Welcome back, adventurer! ⚔️')
      navigate('/dashboard')
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? 'Invalid credentials')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden">
      {/* Background orbs */}
      <div className="absolute top-[-20%] left-[-10%] w-[60vw] h-[60vw] max-w-2xl max-h-2xl rounded-full opacity-[0.07]"
        style={{ background: 'radial-gradient(circle, #7c3aed, transparent 70%)' }} />
      <div className="absolute bottom-[-20%] right-[-10%] w-[50vw] h-[50vw] max-w-xl max-h-xl rounded-full opacity-[0.05]"
        style={{ background: 'radial-gradient(circle, #06b6d4, transparent 70%)' }} />

      <motion.div
        initial={{ opacity: 0, y: 24, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
        className="w-full max-w-sm relative z-10"
      >
        {/* Logo */}
        <div className="flex flex-col items-center mb-8 gap-3">
          <motion.div
            animate={{ boxShadow: ['0 0 10px rgba(124,58,237,0.3)', '0 0 30px rgba(124,58,237,0.7)', '0 0 10px rgba(124,58,237,0.3)'] }}
            transition={{ duration: 2.5, repeat: Infinity, ease: 'easeInOut' }}
            className="w-14 h-14 rounded-2xl bg-gradient-brand flex items-center justify-center"
          >
            <Zap className="w-7 h-7 text-white" fill="white" />
          </motion.div>
          <div className="text-center">
            <h1 className="text-3xl font-bold text-gradient-brand">Questr</h1>
            <p className="text-sm text-slate-500 mt-1">Level up your life, one quest at a time</p>
          </div>
        </div>

        {/* Card */}
        <div className="card p-6 space-y-5">
          <div>
            <h2 className="text-xl font-semibold text-white">Welcome back</h2>
            <p className="text-sm text-slate-500 mt-1">Continue your adventure</p>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-slate-400">Username or Email</label>
              <input
                {...register('usernameOrEmail')}
                className="input-field"
                placeholder="hero@questr.app"
                autoComplete="username"
              />
              {errors.usernameOrEmail && (
                <p className="text-xs text-red-400">{errors.usernameOrEmail.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-slate-400">Password</label>
              <div className="relative">
                <input
                  {...register('password')}
                  type={showPw ? 'text' : 'password'}
                  className="input-field pr-11"
                  placeholder="••••••••"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPw(!showPw)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                >
                  {showPw ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {errors.password && (
                <p className="text-xs text-red-400">{errors.password.message}</p>
              )}
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="btn-brand w-full justify-center py-3 text-base mt-2"
            >
              {isSubmitting ? <Spinner size="sm" /> : (
                <><span>Enter the Quest</span><ArrowRight className="w-4 h-4" /></>
              )}
            </button>
          </form>
        </div>

        <p className="text-center text-sm text-slate-500 mt-5">
          New adventurer?{' '}
          <Link to="/signup" className="text-brand-400 hover:text-brand-300 font-medium transition-colors">
            Create account <Sparkles className="w-3 h-3 inline" />
          </Link>
        </p>
      </motion.div>
    </div>
  )
}

