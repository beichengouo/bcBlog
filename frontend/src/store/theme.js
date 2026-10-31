import { defineStore } from 'pinia'

const THEME_KEY = 'bcblog-theme'

function initialTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch { /* Use the site's default if storage is unavailable. */ }
  return 'dark'
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    // 首次进入默认星空暗色；继续尊重访客主动保存的主题。
    mode: initialTheme()
  }),
  getters: {
    isDark: (state) => state.mode === 'dark'
  },
  actions: {
    toggle() {
      this.setMode(this.mode === 'dark' ? 'light' : 'dark')
    },
    setMode(mode) {
      this.mode = mode
      try { localStorage.setItem(THEME_KEY, mode) } catch { /* The theme can still change in memory. */ }
      this.apply()
    },
    // 把主题写到 html 标签，供 CSS 变量切换
    apply() {
      document.documentElement.setAttribute('data-theme', this.mode)
      // 同步 Element Plus 的暗色模式变量
      document.documentElement.classList.toggle('dark', this.mode === 'dark')
    }
  }
})
