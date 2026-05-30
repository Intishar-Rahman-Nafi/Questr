/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#f5f3ff',
          100: '#ede9fe',
          200: '#ddd6fe',
          300: '#c4b5fd',
          400: '#a78bfa',
          500: '#8b5cf6',
          600: '#7c3aed',
          700: '#6d28d9',
          800: '#5b21b6',
          900: '#4c1d95',
          950: '#2e1065',
        },
        bg: {
          base:     '#0a0a0f',
          card:     '#111118',
          elevated: '#1a1a2e',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        'glow-brand':    '0 0 24px rgba(124,58,237,0.45)',
        'glow-brand-sm': '0 0 12px rgba(124,58,237,0.3)',
        'glow-cyan':     '0 0 24px rgba(6,182,212,0.4)',
        'glow-green':    '0 0 16px rgba(34,197,94,0.4)',
        'card':          '0 4px 24px rgba(0,0,0,0.5)',
        'card-hover':    '0 8px 40px rgba(0,0,0,0.6)',
      },
      animation: {
        'shimmer':      'shimmer 2s linear infinite',
        'float':        'float 4s ease-in-out infinite',
        'pulse-glow':   'pulse-glow 2.5s ease-in-out infinite',
        'slide-up':     'slide-up 0.35s ease-out',
        'scale-in':     'scale-in 0.2s ease-out',
        'fade-in':      'fade-in 0.3s ease-out',
        'count-up':     'count-up 0.6s ease-out',
        'spin-slow':    'spin 4s linear infinite',
        'bounce-soft':  'bounce-soft 1.5s ease-in-out infinite',
      },
      keyframes: {
        shimmer: {
          '0%':   { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(100%)' },
        },
        float: {
          '0%,100%': { transform: 'translateY(0px)' },
          '50%':     { transform: 'translateY(-10px)' },
        },
        'pulse-glow': {
          '0%,100%': { boxShadow: '0 0 8px rgba(124,58,237,0.3)' },
          '50%':     { boxShadow: '0 0 28px rgba(124,58,237,0.7)' },
        },
        'slide-up': {
          '0%':   { transform: 'translateY(20px)', opacity: '0' },
          '100%': { transform: 'translateY(0)',    opacity: '1' },
        },
        'scale-in': {
          '0%':   { transform: 'scale(0.92)', opacity: '0' },
          '100%': { transform: 'scale(1)',    opacity: '1' },
        },
        'fade-in': {
          '0%':   { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'bounce-soft': {
          '0%,100%': { transform: 'translateY(0)' },
          '50%':     { transform: 'translateY(-4px)' },
        },
      },
    },
  },
  plugins: [],
}

