<template>
  <div ref="containerRef" class="game-hero-motion">
    <img class="motion-poster" src="/games/pixelrogue/hero.webp" alt="轮舞于星辉尽头主菜单美术" width="1920" height="1080" />
    <video ref="videoRef" class="motion-video" :class="{ ready: videoReady }" :src="videoSrc || undefined" muted loop playsinline preload="metadata" aria-label="与游戏首页相同的星空循环背景" @canplay="syncPlayback" @playing="onPlaying" @error="onError" />
    <button type="button" class="motion-control" :disabled="reducedMotion" :aria-pressed="!manuallyPaused && !videoError && !reducedMotion" @click="togglePlayback">
      <span class="motion-indicator" :class="{ playing: !manuallyPaused && !videoError && !reducedMotion }" aria-hidden="true"><i></i><i></i><i></i></span>
      {{ reducedMotion ? '动效已简化' : videoError ? '重试背景动画' : manuallyPaused ? '播放背景动画' : '暂停背景动画' }}
    </button>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
const containerRef = ref(null)
const videoRef = ref(null)
const videoSrc = ref('')
const videoReady = ref(false)
const videoError = ref(false)
const manuallyPaused = ref(false)
const reducedMotion = ref(false)
let inView = true
let disposed = false
let observer
let motionQuery

function syncPlayback() {
  const video = videoRef.value
  if (disposed || !video || !videoSrc.value) return
  if (reducedMotion.value || manuallyPaused.value || videoError.value || !inView || document.hidden) {
    video.pause()
    return
  }
  video.muted = true
  const request = video.play()
  request?.catch((error) => {
    if (error?.name !== 'AbortError' && !disposed && inView && !document.hidden && !reducedMotion.value && !videoError.value) manuallyPaused.value = true
  })
}
function onPlaying() { videoReady.value = true; videoError.value = false }
function onError() { videoError.value = true; videoReady.value = false }
function togglePlayback() {
  if (reducedMotion.value) return
  if (videoError.value) {
    videoError.value = false
    manuallyPaused.value = false
    videoRef.value?.load()
  } else manuallyPaused.value = !manuallyPaused.value
  syncPlayback()
}
function onMotionPreference(event) { reducedMotion.value = event.matches; syncPlayback() }
onMounted(async () => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener?.('change', onMotionPreference)
  videoSrc.value = window.matchMedia('(max-width: 800px)').matches ? '/games/pixelrogue/menu-mobile.mp4' : '/games/pixelrogue/menu-desktop.mp4'
  document.addEventListener('visibilitychange', syncPlayback)
  if (window.IntersectionObserver) {
    observer = new window.IntersectionObserver(([entry]) => { inView = entry.isIntersecting; syncPlayback() }, { threshold: 0 })
    observer.observe(containerRef.value)
  }
  await nextTick()
  syncPlayback()
})
onUnmounted(() => {
  disposed = true
  observer?.disconnect()
  motionQuery?.removeEventListener?.('change', onMotionPreference)
  document.removeEventListener('visibilitychange', syncPlayback)
  videoRef.value?.pause()
})
</script>

<style scoped>
.game-hero-motion { position: absolute; inset: 0; z-index: -2; overflow: hidden; background: #0b101b; }
.motion-poster, .motion-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center; }
.motion-video { opacity: 0; transition: opacity 1.2s ease; }
.motion-video.ready { opacity: 1; }
.motion-control { position: absolute; top: 28px; right: 32px; z-index: 3; display: inline-flex; gap: 12px; align-items: center; padding: 11px 15px; border: 1px solid #d6c6a33d; border-radius: 30px; background: #0b101ba6; backdrop-filter: blur(12px); color: #e5dbc8; font-size: 10px; cursor: pointer; }
.motion-control:disabled { cursor: default; opacity: .7; }
.motion-indicator { display: inline-flex; align-items: center; gap: 3px; height: 12px; }
.motion-indicator i { width: 2px; height: 5px; border-radius: 2px; background: #d9c6a1; }
.motion-indicator.playing i { animation: motion-wave 1.1s ease-in-out infinite alternate; }
.motion-indicator.playing i:nth-child(2) { animation-delay: -.4s; }
.motion-indicator.playing i:nth-child(3) { animation-delay: -.7s; }
@keyframes motion-wave { to { height: 12px; } }
@media (max-width: 600px) { .motion-control { top: 20px; right: 20px; padding: 9px 12px; font-size: 9px; } }
</style>
