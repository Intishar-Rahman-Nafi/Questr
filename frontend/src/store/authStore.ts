import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

interface AuthState {
  accessToken:  string | null
  refreshToken: string | null
  userId:       string | null
  username:     string | null
  email:        string | null
  isAuthenticated: boolean
  login:     (d: { accessToken:string; refreshToken:string; userId:string; username:string; email:string }) => void
  setTokens: (at: string, rt: string) => void
  logout:    () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null, refreshToken: null, userId: null,
      username: null, email: null, isAuthenticated: false,

      login: (d) => set({ ...d, isAuthenticated: true }),

      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),

      logout: () => set({
        accessToken: null, refreshToken: null, userId: null,
        username: null, email: null, isAuthenticated: false,
      }),
    }),
    {
      name: 'questr-auth',
      storage: createJSONStorage(() => localStorage),
    },
  ),
)

