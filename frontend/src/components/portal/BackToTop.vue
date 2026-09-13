<template>
  <transition name="top-fade">
    <button v-if="visible" class="back-to-top" @click="scrollTop" title="回到顶部" aria-label="回到顶部">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 19V5M5 12l7-7 7 7" />
      </svg>
    </button>
  </transition>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const visible = ref(false)

function onScroll() {
  visible.value = window.scrollY > 360
}

function scrollTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.back-to-top {
  position: fixed;
  right: 26px;
  bottom: 340px;
  z-index: 70;
  width: 42px;
  height: 42px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--glass-bg);
  color: var(--text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: var(--shadow);
  transition: transform 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}
.back-to-top:hover {
  transform: translateY(-3px);
  color: var(--accent);
  border-color: var(--accent);
}
.top-fade-enter-active,
.top-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.top-fade-enter-from,
.top-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
