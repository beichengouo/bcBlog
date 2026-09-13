<template>
  <div class="progress" :style="{ width: percent + '%' }"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const percent = ref(0)

function update() {
  const scrollTop = window.scrollY
  const height = document.documentElement.scrollHeight - window.innerHeight
  percent.value = height > 0 ? Math.min(100, (scrollTop / height) * 100) : 0
}

onMounted(() => {
  update()
  window.addEventListener('scroll', update, { passive: true })
})

onUnmounted(() => window.removeEventListener('scroll', update))
</script>

<style scoped>
.progress {
  position: fixed;
  top: 0;
  left: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
  z-index: 100;
  transition: width 0.1s linear;
  border-radius: 0 2px 2px 0;
}
</style>
