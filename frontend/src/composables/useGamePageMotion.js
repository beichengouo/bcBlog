import { ref, onMounted, onUnmounted } from 'vue'

export function useGamePageMotion(root) {
  const activeSection = ref('game-intro')
  const progress = ref(0)
  let revealObserver, sectionObserver, preference
  let frame = 0
  let disposed = false
  function updateProgress() {
    frame = 0
    if (disposed || !root.value) return
    const bounds = root.value.getBoundingClientRect()
    progress.value = Math.max(0, Math.min(1, -bounds.top / Math.max(1, bounds.height - window.innerHeight)))
  }
  function onScroll() { if (!frame) frame = window.requestAnimationFrame(updateProgress) }
  function configureSections() {
    sectionObserver?.disconnect()
    if (!window.IntersectionObserver || disposed) return
    sectionObserver = new window.IntersectionObserver((entries) => {
      if (disposed) return
      for (const entry of entries) if (entry.isIntersecting) activeSection.value = entry.target.id
    }, { rootMargin: `-${Math.round(window.innerHeight * .15)}px 0px -${Math.round(window.innerHeight * .55)}px 0px`, threshold: 0 })
    root.value?.querySelectorAll('section[id]').forEach((section) => sectionObserver.observe(section))
  }
  function onResize() { configureSections(); onScroll() }
  function configureReveal() {
    revealObserver?.disconnect()
    if (!root.value) return
    const targets = root.value.querySelectorAll('.game-reveal')
    const enabled = !preference.matches && !!window.IntersectionObserver
    root.value.classList.toggle('reveal-ready', enabled)
    if (!enabled) { targets.forEach((target) => target.classList.add('is-visible')); return }
    revealObserver = new window.IntersectionObserver((entries) => {
      if (disposed) return
      entries.forEach((entry) => {
        if (entry.isIntersecting) { entry.target.classList.add('is-visible'); revealObserver.unobserve(entry.target) }
      })
    }, { threshold: 0.08, rootMargin: '0px 0px -32px 0px' })
    targets.forEach((target, index) => {
      target.style.setProperty('--enter-delay', `${(index % 2) * 85}ms`)
      revealObserver.observe(target)
    })
  }
  onMounted(() => {
    preference = window.matchMedia('(prefers-reduced-motion: reduce)')
    configureReveal()
    preference.addEventListener?.('change', configureReveal)
    configureSections()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onResize, { passive: true })
    updateProgress()
  })
  onUnmounted(() => {
    disposed = true
    revealObserver?.disconnect()
    sectionObserver?.disconnect()
    preference?.removeEventListener?.('change', configureReveal)
    window.removeEventListener('scroll', onScroll)
    window.removeEventListener('resize', onResize)
    if (frame) window.cancelAnimationFrame(frame)
  })
  return { activeSection, progress }
}
