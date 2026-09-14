import { defineStore } from 'pinia'
import { loginMember, registerMember, logoutMember, getMemberInfo } from '@/api/member'

const TOKEN_KEY = 'memberToken'

/** 前台普通用户状态，与后台管理员 token 分开存储 */
export const useMemberStore = defineStore('member', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: null,
    loaded: false
  }),
  getters: {
    isLogin: (state) => !!state.token
  },
  actions: {
    setToken(token) {
      this.token = token || ''
      if (token) {
        localStorage.setItem(TOKEN_KEY, token)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }
    },
    clear() {
      this.token = ''
      this.userInfo = null
      this.loaded = false
      localStorage.removeItem(TOKEN_KEY)
    },
    async login(payload) {
      const data = await loginMember(payload)
      this.setToken(data.token)
      this.userInfo = data.user
      this.loaded = true
      return data
    },
    async register(payload) {
      const data = await registerMember(payload)
      this.setToken(data.token)
      this.userInfo = data.user
      this.loaded = true
      return data
    },
    async logout() {
      try {
        await logoutMember()
      } catch (e) {
        // 忽略退出接口异常
      } finally {
        this.clear()
      }
    },
    async fetchInfo(force = false) {
      if (!this.token) {
        this.userInfo = null
        return null
      }
      if (this.loaded && !force && this.userInfo) {
        return this.userInfo
      }
      try {
        this.userInfo = await getMemberInfo()
        this.loaded = true
        return this.userInfo
      } catch (e) {
        this.clear()
        return null
      }
    }
  }
})
