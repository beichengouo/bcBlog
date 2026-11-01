import { reactive } from 'vue'
export const navigationLoading = reactive({ visible: false, done: false, error: false, retryPath: '' })
let activeTarget, revealTimer, finishTimer, awaitingArticle = false
function clearTimers() { clearTimeout(revealTimer); clearTimeout(finishTimer) }
export function startNavigation(target) {
  clearTimers(); activeTarget = target
  awaitingArticle = /^\/portal\/article\/[^/]+/.test(target.path || target.fullPath || '')
  navigationLoading.done = false; navigationLoading.error = false; navigationLoading.retryPath = ''
  if (awaitingArticle) navigationLoading.visible = true
  revealTimer = setTimeout(() => { navigationLoading.visible = true }, 160)
}
export function finishNavigation(target, failed = false) {
  if (!target || target !== activeTarget) return
  if (awaitingArticle && !failed) return
  clearTimers(); activeTarget = null; awaitingArticle = false
  navigationLoading.done = !failed
  finishTimer = setTimeout(() => { navigationLoading.visible = false; navigationLoading.done = false }, failed ? 0 : 200)
}
export function failNavigation(target) {
  if (!target || target !== activeTarget) return
  clearTimers(); activeTarget = null
  navigationLoading.visible = false; navigationLoading.done = false; navigationLoading.error = true; navigationLoading.retryPath = target.fullPath
}

export function completeArticleNavigation(path) {
  if (activeTarget?.fullPath !== path) return
  awaitingArticle = false
  finishNavigation(activeTarget)
}
export function waitForArticleCover(source, timeout = 8000) {
  if (!source) return Promise.resolve()
  return new Promise(resolve => {
    const image = new Image(); let finished = false
    const done = () => { if (finished) return; finished = true; clearTimeout(timer); image.onload = image.onerror = null; resolve() }
    const timer = setTimeout(done, timeout)
    image.onload = async () => { try { await image.decode?.() } catch {} done() }
    image.onerror = done; image.src = source
  })
}
