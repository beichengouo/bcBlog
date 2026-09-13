<template>
  <div v-if="bg" class="bg-layer">
    <video v-if="bg.type === 'video'" :src="bg.url" :style="mediaStyle" autoplay muted loop playsinline></video>
    <img v-else :src="bg.url" :style="mediaStyle" alt="" />
    <div class="bg-mask"></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getActiveBackground } from '@/api/background'
import { getAdminBgOpacity } from '@/api/config'

const props = defineProps({
  scope: { type: String, default: 'portal' }
})

const bg = ref(null)
const adminOpacity = ref(1)

// 后台背景支持透明度调节，前台背景保持原样
const mediaStyle = computed(() => (props.scope === 'admin' ? { opacity: adminOpacity.value } : {}))

onMounted(async () => {
  try {
    bg.value = await getActiveBackground(props.scope)
  } catch (e) {
    // 未配置背景时忽略
  }
  if (props.scope === 'admin') {
    try {
      adminOpacity.value = await getAdminBgOpacity()
    } catch (e) {
      // 读取失败时使用默认不透明
    }
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
