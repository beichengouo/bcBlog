<template>
  <div ref="root" class="ripple-root" aria-hidden="true"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const root = ref(null)

function spawn(x, y) {
  if (!root.value) return
  const el = document.createElement('span')
  el.className = 'ripple'
  el.style.left = x + 'px'
  el.style.top = y + 'px'
  root.value.appendChild(el)
  el.addEventListener('animationend', () => el.remove())
}

function onDown(e) {
  spawn(e.clientX, e.clientY)
}

onMounted(() => window.addEventListener('pointerdown', onDown, { passive: true }))
onUnmounted(() => window.removeEventListener('pointerdown', onDown))
</script>

<style scoped>
.ripple-root {
  position: fixed;
  inset: 0;
  z-index: 9998;
  pointer-events: none;
}
.ripple {
  position: absolute;
  width: 12px;
  height: 12px;
  margin: -6px 0 0 -6px;
  border-radius: 50%;
  border: 2px solid var(--accent);
  background: var(--accent-soft);
  animation: ripple 0.55s ease-out forwards;
}
@keyframes ripple {
  from {
    transform: scale(0.35);
    opacity: 0.9;
  }
  to {
    transform: scale(5);
    opacity: 0;
  }
}
</style>
