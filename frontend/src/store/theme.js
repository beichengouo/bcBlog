import { defineStore } from 'pinia'
import { beijingTheme } from '@/utils/themeTime'
const THEME_KEY = 'bcblog-theme-preference'
function initialPreference() {
  try { const saved = localStorage.getItem(THEME_KEY); if (saved === 'light' || saved === 'dark') return saved } catch {}
  return 'auto'
}
export const useThemeStore = defineStore('theme', {
  state: () => { const preference = initialPreference(); return { preference, mode: preference === 'auto' ? beijingTheme() : preference } },
  getters: { isDark: state => state.mode === 'dark' },
  actions: {
    toggle() { this.setMode(this.isDark ? 'light' : 'dark') },
    setMode(preference) {
      if (!['auto','light','dark'].includes(preference)) return
      this.preference = preference
      try { localStorage.setItem(THEME_KEY, preference) } catch {}
      this.syncTime()
    },
    syncTime() { this.mode = this.preference === 'auto' ? beijingTheme() : this.preference; this.apply() },
    apply() { document.documentElement.setAttribute('data-theme', this.mode); document.documentElement.classList.toggle('dark', this.isDark) }
  }
})
