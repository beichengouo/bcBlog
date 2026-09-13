<template>
  <div v-if="bg" class="bg-layer">
    <video v-if="bg.type === 'video'" :src="bg.url" autoplay muted loop playsinline></video>
    <img v-else :src="bg.url" alt="" />
    <div class="bg-mask"></div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getActiveBackground } from '@/api/background'

const props = defineProps({
  scope: { type: String, default: 'portal' }
})

const bg = ref(null)

onMounted(async () => {
  try {
    bg.value = await getActiveBackground(props.scope)
  } catch (e) {
    // 未配置背景时忽略
  }
})
</script>

<style scoped>
.bg-layer {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  background: var(--bg);
}
.bg-layer img,
.bg-layer video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.bg-mask {
  position: absolute;
  inset: 0;
  background: var(--bg-mask);
}
</style>
