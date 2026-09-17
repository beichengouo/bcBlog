import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getInfo } from '@/api/auth'
import { clearSecurityUnlock } from '@/utils/securityGate'

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
      // 退出登录时清空"已通过二次验证的菜单"，下次登录必须重新验证
      clearSecurityUnlock()
    },
    async login(payload) {
      const data = await loginApi(payload)
      // 每次新登录都清空"已解锁菜单"，避免上一个会话的解锁记录被沿用
      clearSecurityUnlock()
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
