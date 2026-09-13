import { defineStore } from 'pinia'
import { getPortalConfig } from '@/api/config'

// 站点配置缓存，前台多个页面共用
export const useSiteStore = defineStore('site', {
  state: () => ({
    config: null,
    loaded: false
  }),
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return this.config
      try {
        this.config = await getPortalConfig()
      } catch (e) {
        this.config = {}
      }
      this.loaded = true
      return this.config
    }
  }
})
