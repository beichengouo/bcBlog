<template>
  <div v-if="bg" class="bg-layer">
    <video v-if="bg.type === 'video'" :src="bg.url" :style="mediaStyle" autoplay muted loop playsinline></video>
    <img v-else :src="bg.url" :style="mediaStyle" alt="" />
    <div class="bg-mask"></div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getActiveBackground } from '@/api/background'
import { getAdminBgOpacity } from '@/api/config'

const props = defineProps({
  scope: { type: String, default: 'portal' }
})

const route = useRoute()
const bg = ref(null)
const adminOpacity = ref(1)
const pageOpacity = ref(1)

/** 前台按页面独立设置背景：把路由映射成后台配置用的页面标识 */
const PAGE_PREFIXES = [
  { prefix: '/portal/photos', page: 'photos' },
  { prefix: '/portal/resources', page: 'resources' },
  { prefix: '/portal/sandbox', page: 'sandbox' }
]

const pageKey = computed(() => {
  if (props.scope !== 'portal') return null
  const path = route.path || ''
  const hit = PAGE_PREFIXES.find((item) => path.startsWith(item.prefix))
  if (hit) return hit.page
  // 首页；其它页面（文章详情、用户中心等）不传 page，走前台默认壁纸
  if (path === '/' || path === '/portal' || path === '/portal/') return 'home'
  return null
})

// 后台壁纸用「后台背景透明度」，前台各页面用各自配置的透明度
const mediaStyle = computed(() => ({
  opacity: props.scope === 'admin' ? adminOpacity.value : pageOpacity.value
}))

async function load() {
  try {
    const data = await getActiveBackground(props.scope, pageKey.value)
    bg.value = data && data.url ? data : null
    pageOpacity.value = data && data.opacity != null ? Number(data.opacity) : 1
  } catch (e) {
    // 未配置背景时忽略
    bg.value = null
  }
  if (props.scope === 'admin') {
    try {
      adminOpacity.value = await getAdminBgOpacity()
    } catch (e) {
      // 读取失败时使用默认不透明
    }
  }
}

onMounted(load)
// 切换页面时重新拉取：每个页面可以用不同的壁纸与透明度
watch(() => [props.scope, pageKey.value], load)
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
