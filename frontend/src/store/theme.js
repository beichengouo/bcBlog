import { defineStore } from 'pinia'

const THEME_KEY = 'bcblog-theme'

function systemPrefersDark() {
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    // 首次进入跟随系统，之后用户手动切换会持久化
    mode: localStorage.getItem(THEME_KEY) || (systemPrefersDark() ? 'dark' : 'light')
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
      localStorage.setItem(THEME_KEY, mode)
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
