// Initial-page barrier: data requests first, then decoded cover images.
let accepting = true, pending = 0, requestsPending = 0, failed = 0, videoTotal = 0, videoLoaded = 0, lastChange = Date.now()
const images = new Map()
const videos = new WeakSet()
function changed() { lastChange = Date.now() }
export function trackBootTask() {
  if (!accepting) return null
  pending++; requestsPending++; changed(); let finished = false
  return (success = true) => { if (finished) return; finished = true; pending--; requestsPending--; if (!success) failed++; changed() }
}
export function trackBootRequest(url) {
  if (!accepting || !/^\/portal\/(config$|background$|article(?:\/|$))/.test(url || '')) return null
  const finish = trackBootTask(); let finished = false
  return (success = true, data) => {
    if (finished) return
    finished = true; finish(success)
    if (!accepting || !success) return
    if (url === '/portal/background') { if (data?.type !== 'video') preloadImage(data?.url) }
    else if (url === '/portal/config') preloadImage(data?.siteLogo)
    else { preloadImage(data?.cover); for (const item of data?.list || []) preloadImage(item.cover) }
  }
}
export function preloadImage(source) {
  if (!accepting || !source || typeof source !== 'string') return
  let url
  try { url = new URL(source, window.location.href); if (!['http:','https:','data:','blob:'].includes(url.protocol)) return } catch { return }
  if (images.has(url.href)) return
  images.set(url.href, 'pending'); pending++; changed()
  const image = new Image(); let settled = false
  const finish = (success) => { if (settled) return; settled = true; clearTimeout(timer); image.onload = image.onerror = null; pending--; images.set(url.href,success ? 'loaded' : 'failed'); if (!success) failed++; changed() }
  const timer = setTimeout(() => finish(false), 12000)
  image.onload = async () => { try { if (image.decode) await image.decode(); finish(true) } catch { finish(false) } }
  image.onerror = () => finish(false)
  image.decoding = 'async'; image.src = url.href
}
function discoverImages() {
  const root = document.getElementById('app'); if (!root) return
  for (const image of root.querySelectorAll('img')) { image.loading = 'eager'; preloadImage(image.currentSrc || image.getAttribute('src')) }
  for (const video of root.querySelectorAll('video[src]')) {
    if (videos.has(video)) continue
    videos.add(video); videoTotal++
    if (video.readyState >= 2) { videoLoaded++; continue }
    pending++; changed(); let settled = false
    const finish = (success) => { if (settled) return; settled = true; clearTimeout(timer); video.removeEventListener('loadeddata', ready); video.removeEventListener('error', error); pending--; if (success) videoLoaded++; if (!success) failed++; changed() }
    const ready = () => finish(true), error = () => finish(false)
    const timer = setTimeout(error, 12000)
    video.addEventListener('loadeddata', ready); video.addEventListener('error', error); video.preload = 'auto'
    if (video.readyState >= 2) ready()
  }
  for (const element of root.querySelectorAll('[style]')) {
    const value = element.style.backgroundImage || ''
    for (const match of value.matchAll(/url\(["']?(.+?)["']?\)/g)) preloadImage(match[1])
  }
}
export async function waitForBootAssets({ timeout = 15000, onProgress = () => {} } = {}) {
  const start = Date.now()
  while (true) {
    discoverImages()
    onProgress({ total: images.size + videoTotal, loaded: [...images.values()].filter(status => status === 'loaded').length + videoLoaded, pending, requestsPending, failed })
    if (pending === 0 && Date.now() - lastChange >= 300) { accepting = false; return { failed, timedOut: false } }
    if (Date.now() - start >= timeout) { accepting = false; return { failed, timedOut: true } }
    await new Promise(resolve => setTimeout(resolve, 100))
  }
}
