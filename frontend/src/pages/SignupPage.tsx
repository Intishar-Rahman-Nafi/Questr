import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { motion } from 'framer-motion'
import { Zap, Eye, EyeOff, ArrowRight, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import { authApi } from '@/api'
import { useAuthStore } from '@/store/authStore'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'

const schema = z.object({
  username: z.string().min(3, 'Min 3 characters').max(20, 'Max 20 characters').regex(/^\w+$/, 'Only letters, numbers, _'),
  email:    z.string().email('Valid email required'),
  password: z.string().min(8, 'Min 8 characters'),
})
type Form = z.infer<typeof schema>

function StrengthBar({ password }: { password: string }) {
  const score = [/.{8,}/, /[A-Z]/, /[0-9]/, /[^A-Za-z0-9]/].filter(r => r.test(password)).length
  const labels = ['', 'Weak', 'Fair', 'Good', 'Strong']
  const colors = ['', 'bg-red-500', 'bg-amber-500', 'bg-brand-500', 'bg-green-500']
  if (!password) return null
  return (
    <div className="space-y-1">
      <div className="flex gap-1">
        {[1,2,3,4].map(i => (
          <div key={i} className={cn('h-1 flex-1 rounded-full transition-all duration-300', i <= score ? colors[score] : 'bg-white/10')} />
        ))}
      </div>
      <p className={cn('text-[11px]', score < 2 ? 'text-red-400' : score < 3 ? 'text-amber-400' : score < 4 ? 'text-brand-400' : 'text-green-400')}>
        {labels[score]}
      </p>
    </div>
  )
}

const PERKS = ['Daily XP rewards', 'Achievement badges', 'AI weekly insights', 'Challenge leaderboards']

export function SignupPage() {
  const [showPw, setShowPw] = useState(false)
  const navigate = useNavigate()
  const login = useAuthStore(s => s.login)

  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<Form>({
    resolver: zodResolver(schema),
  })
  const pw = watch('password', '')

  const onSubmit = async (data: Form) => {
    try {
      const res = await authApi.signup(data)
      login(res)
      toast.success('Quest unlocked! Your adventure begins now 🚀')
      navigate('/dashboard')
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? 'Signup failed. Try again.')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden">
      <div className="absolute top-[-15%] right-[-5%] w-[55vw] h-[55vw] max-w-2xl rounded-full opacity-[0.06]"
        style={{ background: 'radial-gradient(circle, #7c3aed, transparent 70%)' }} />
      <div className="absolute bottom-[-15%] left-[-5%] w-[45vw] h-[45vw] max-w-xl rounded-full opacity-[0.05]"
        style={{ background: 'radial-gradient(circle, #06b6d4, transparent 70%)' }} />

      <div className="w-full max-w-4xl relative z-10 grid lg:grid-cols-2 gap-8 items-center">
        {/* Left — value props */}
        <motion.div
          initial={{ opacity: 0, x: -24 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
          className="hidden lg:block"
        >
          <div className="flex items-center gap-3 mb-6">
            <div className="w-12 h-12 rounded-2xl bg-gradient-brand flex items-center justify-center shadow-glow-brand">
              <Zap className="w-6 h-6 text-white" fill="white" />
            </div>
            <span className="text-2xl font-bold text-gradient-brand">Questr</span>
          </div>
          <h2 className="text-4xl font-bold text-white leading-tight mb-4">
            Turn your to-do list<br />
            <span className="text-gradient-brand">into an epic quest</span>
          </h2>
          <p className="text-slate-400 mb-8 leading-relaxed">
            Gamified productivity that actually works. Complete tasks, earn XP, unlock achievements, and compete with friends.
          </p>
          <div className="space-y-3">
            {PERKS.map((perk, i) => (
              <motion.div
                key={perk}
                initial={{ opacity: 0, x: -16 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.2 + i * 0.07 }}
                className="flex items-center gap-3"
              >
                <div className="w-5 h-5 rounded-full bg-gradient-brand flex items-center justify-center flex-shrink-0">
                  <CheckCircle2 className="w-3 h-3 text-white" />
                </div>
                <span className="text-slate-300 text-sm">{perk}</span>
              </motion.div>
            ))}
          </div>
        </motion.div>

        {/* Right — form */}
        <motion.div
          initial={{ opacity: 0, y: 24, scale: 0.97 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
        >
          {/* Mobile logo */}
          <div className="flex flex-col items-center mb-6 gap-2 lg:hidden">
            <div className="w-12 h-12 rounded-xl bg-gradient-brand flex items-center justify-center shadow-glow-brand">
              <Zap className="w-6 h-6 text-white" fill="white" />
            </div>
            <span className="text-2xl font-bold text-gradient-brand">Questr</span>
          </div>

          <div className="card p-6 space-y-5">
            <div>
              <h2 className="text-xl font-semibold text-white">Start your journey</h2>
              <p className="text-sm text-slate-500 mt-1">Free forever. No credit card needed.</p>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-400">Username</label>
                <input {...register('username')} className="input-field" placeholder="heroic_name" autoComplete="username" />
                {errors.username && <p className="text-xs text-red-400">{errors.username.message}</p>}
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-400">Email</label>
                <input {...register('email')} type="email" className="input-field" placeholder="you@example.com" autoComplete="email" />
                {errors.email && <p className="text-xs text-red-400">{errors.email.message}</p>}
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-400">Password</label>
                <div className="relative">
                  <input
                    {...register('password')}
                    type={showPw ? 'text' : 'password'}
                    className="input-field pr-11"
                    placeholder="Min 8 characters"
                    autoComplete="new-password"
                  />
                  <button type="button" onClick={() => setShowPw(!showPw)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors">
                    {showPw ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                <StrengthBar password={pw} />
                {errors.password && <p className="text-xs text-red-400">{errors.password.message}</p>}
              </div>

              <button type="submit" disabled={isSubmitting} className="btn-brand w-full justify-center py-3 text-base mt-2">
                {isSubmitting ? <Spinner size="sm" /> : (
                  <><span>Begin the Quest</span><ArrowRight className="w-4 h-4" /></>
                )}
              </button>
            </form>

            <p className="text-center text-xs text-slate-600">
              By signing up you agree to our Terms of Service and Privacy Policy
            </p>
          </div>

          <p className="text-center text-sm text-slate-500 mt-5">
            Already an adventurer?{' '}
            <Link to="/login" className="text-brand-400 hover:text-brand-300 font-medium transition-colors">
              Sign in →
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  )
}

