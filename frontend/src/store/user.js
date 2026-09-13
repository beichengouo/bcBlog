import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getInfo } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: null
  }),
  actions: {
    setToken(token) {
      this.token = token
      localStorage.setItem('token', token)
    },
    clear() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
    },
    async login(payload) {
      const data = await loginApi(payload)
      this.setToken(data.token)
      this.userInfo = data.user
      return data
    },
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 忽略登出接口异常，本地状态照常清理
      } finally {
        this.clear()
      }
    },
    async fetchInfo() {
      this.userInfo = await getInfo()
      return this.userInfo
    }
  }
})
