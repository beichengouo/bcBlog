<template>
  <canvas ref="canvas" class="particle-canvas" :class="{ off: !active }" aria-hidden="true"></canvas>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useThemeStore } from '@/store/theme'

const themeStore = useThemeStore()
const canvas = ref(null)
const props = defineProps({
  active: { type: Boolean, default: true }
})
let ctx = null
let rafId = 0
let running = false
let particles = []
let W = 0
let H = 0
let dpr = 1

function resize() {
  if (!canvas.value) return
  dpr = Math.min(window.devicePixelRatio || 1, 2)
  W = window.innerWidth
  H = window.innerHeight
  canvas.value.width = W * dpr
  canvas.value.height = H * dpr
  canvas.value.style.width = W + 'px'
  canvas.value.style.height = H + 'px'
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  build()
}

function rand(min, max) {
  return Math.random() * (max - min) + min
}

// 白天：飘落的樱花花瓣；夜晚：闪烁星空 + 偶发流星
function build() {
  const dark = themeStore.isDark
  const count = Math.min(160, Math.floor((W * H) / 9000))
  particles = []
  for (let i = 0; i < count; i++) {
    if (dark) {
      particles.push({
        type: 'star',
        x: rand(0, W),
        y: rand(0, H),
        r: rand(0.4, 1.7),
        tw: rand(0.008, 0.03),
        phase: rand(0, Math.PI * 2)
      })
    } else {
      particles.push({
        type: 'petal',
        x: rand(0, W),
        y: rand(-H, 0),
        size: rand(5, 12),
        vy: rand(0.6, 1.6),
        sway: rand(0.3, 1.0),
        rot: rand(0, Math.PI * 2),
        vr: rand(-0.03, 0.03),
        hue: rand(-8, 8)
      })
    }
  }
}

function draw() {
  if (!running) return
  ctx.clearRect(0, 0, W, H)
  const dark = themeStore.isDark
  for (const p of particles) {
    if (dark) {
      const a = 0.45 + Math.abs(Math.sin(p.phase)) * 0.55
      ctx.beginPath()
      ctx.fillStyle = `rgba(190, 205, 255, ${a})`
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fill()
      p.phase += p.tw
    } else {
      p.y += p.vy
      p.x += Math.sin((p.y * 0.02) + p.rot) * p.sway
      p.rot += p.vr
      if (p.y > H + 20) {
        p.y = -20
        p.x = rand(0, W)
      }
      ctx.save()
      ctx.translate(p.x, p.y)
      ctx.rotate(p.rot)
      ctx.beginPath()
      ctx.fillStyle = `hsla(${345 + p.hue}, 88%, 72%, 0.85)`
      ctx.ellipse(0, 0, p.size, p.size * 0.5, 0, 0, Math.PI * 2)
      ctx.fill()
      ctx.restore()
    }
  }
  rafId = requestAnimationFrame(draw)
}

function start() {
  if (running || !ctx) return
  running = true
  draw()
}

function stop() {
  running = false
  cancelAnimationFrame(rafId)
}

onMounted(() => {
  ctx = canvas.value.getContext('2d')
  resize()
  if (props.active) start()
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  stop()
  window.removeEventListener('resize', resize)
})

watch(() => themeStore.isDark, build)
watch(() => props.active, (v) => (v ? start() : stop()))
</script>

<style scoped>
.particle-canvas {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  transition: opacity 1.2s ease;
}
.particle-canvas.off {
  opacity: 0;
}
</style>
