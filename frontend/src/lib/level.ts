/** XP required to reach a given level: xp = 100*L² - 10*L */
export function xpForLevel(level: number): number {
  return 100 * level * level - 10 * level
}

/** Level from total XP: floor((sqrt(100*xp+25)+5)/100) */
export function levelFromXp(xp: number): number {
  return Math.floor((Math.sqrt(100 * xp + 25) + 5) / 100)
}

/** Percentage progress toward the next level (0–100) */
export function xpProgressPercent(totalXp: number, level: number): number {
  // Backend uses max(1, floor(...)) so level 1 starts at 0 XP, not xpForLevel(1)=90
  const currentLevelXp = level <= 1 ? 0 : xpForLevel(level)
  const nextLevelXp    = xpForLevel(level + 1)
  const range  = nextLevelXp - currentLevelXp
  const gained = totalXp - currentLevelXp
  return Math.min(100, Math.max(0, Math.round((gained / range) * 100)))
}

/** XP still needed to reach the next level */
export function xpToNextLevel(totalXp: number, level: number): number {
  return xpForLevel(level + 1) - totalXp
}

/** Human-readable level title based on level number */
export function levelTitle(level: number): string {
  if (level < 3)  return 'Novice'
  if (level < 6)  return 'Apprentice'
  if (level < 10) return 'Journeyman'
  if (level < 15) return 'Expert'
  if (level < 20) return 'Master'
  if (level < 30) return 'Grandmaster'
  return 'Legend'
}

/** Emoji for the level tier */
export function levelEmoji(level: number): string {
  if (level < 3)  return '🌱'
  if (level < 6)  return '⚔️'
  if (level < 10) return '🔮'
  if (level < 15) return '🏆'
  if (level < 20) return '👑'
  if (level < 30) return '💎'
  return '🌟'
}

