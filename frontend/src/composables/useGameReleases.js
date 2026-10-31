import { ref, onMounted, onUnmounted } from 'vue'
import { portalGamePackages } from '@/api/gamePackages'
import { packageDownloadUrl } from '@/utils/gamePackages'

export function useGameReleases() {
  const packages = ref({}), loading = ref(true), failed = ref(false)
  let disposed = false, pending = false, timer
  async function load() {
    if (pending || disposed) return
    pending = true
    try {
      const data = await portalGamePackages()
      if (!Array.isArray(data)) throw new Error('Invalid release response')
      if (!disposed) {
        packages.value = Object.fromEntries(data.filter(item => item.published && packageDownloadUrl(item.platform) && typeof item.version === 'string' && item.version.trim()).map(item => [item.platform, item]))
        failed.value = false
      }
    } catch {
      if (!disposed) { packages.value = {}; failed.value = true }
    } finally { pending = false; if (!disposed) loading.value = false }
  }
  function refreshVisible() { if (!document.hidden) load() }
  onMounted(() => {
    load()
    timer = window.setInterval(refreshVisible, 30000)
    window.addEventListener('focus', refreshVisible)
    document.addEventListener('visibilitychange', refreshVisible)
  })
  onUnmounted(() => {
    disposed = true
    window.clearInterval(timer)
    window.removeEventListener('focus', refreshVisible)
    document.removeEventListener('visibilitychange', refreshVisible)
  })
  return { packages, loading, failed, load }
}
