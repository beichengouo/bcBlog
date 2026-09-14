<template>
  <div class="posts-carousel glass">
    <template v-if="posts.length">
      <div class="slide" @click="go(current.id)">
        <img v-if="current.cover" class="slide-cover" :src="current.cover" alt="" />
        <div v-else class="slide-cover fallback"></div>
        <div class="slide-mask"></div>
        <div class="slide-body">
          <div class="slide-tags">
            <span class="tag">最新文章</span>
            <span v-if="current.createTime" class="date">{{ shortDate(current.createTime) }}</span>
          </div>
          <h3 class="slide-title">{{ current.title }}</h3>
          <p v-if="current.summary" class="slide-summary">{{ current.summary }}</p>
        </div>
      </div>
      <div v-if="posts.length > 1" class="dots">
        <button
          v-for="(p, i) in posts"
          :key="p.id"
          :class="{ active: i === currentIndex }"
          @click.stop="currentIndex = i"
          :aria-label="`切换到第 ${i + 1} 篇`"
        ></button>
      </div>
    </template>
    <el-empty v-else description="暂无文章" :image-size="70" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { listPortalArticles } from '@/api/article'

const props = defineProps({
  count: { type: Number, default: 5 }
})

const router = useRouter()
const posts = ref([])
const currentIndex = ref(0)
let timer = 0

const current = computed(() => posts.value[currentIndex.value] || {})

function shortDate(dt) {
  return dt ? String(dt).slice(0, 10) : ''
}

function go(id) {
  if (!id) return
  router.push(`/portal/article/${id}`)
}

async function load() {
  try {
    const size = Math.max(1, Math.min(10, props.count || 5))
    const data = await listPortalArticles({ page: 1, size })
    posts.value = data.list || []
    currentIndex.value = 0
  } catch (e) {
    posts.value = []
  }
  startTimer()
}

function startTimer() {
  clearInterval(timer)
  if (posts.value.length <= 1) return
  timer = setInterval(() => {
    currentIndex.value = (currentIndex.value + 1) % posts.value.length
  }, 5000)
}

watch(() => props.count, load)
onMounted(load)
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.posts-carousel {
  position: relative;
  height: 100%;
  min-height: 300px;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}
.slide {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 300px;
  cursor: pointer;
}
.slide-cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.8s ease;
}
.slide:hover .slide-cover {
  transform: scale(1.05);
}
.slide-cover.fallback {
  background:
    radial-gradient(circle at 20% 20%, var(--accent-soft), transparent 60%),
    radial-gradient(circle at 80% 80%, var(--accent-soft), transparent 60%),
    var(--card-solid);
}
.slide-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.86), rgba(0, 0, 0, 0.28) 55%, rgba(0, 0, 0, 0.05));
}
.slide-body {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20px;
  z-index: 1;
}
.slide-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.tag {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  color: #fff;
  background: var(--accent);
}
.date {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
}
.slide-title {
  margin: 0 0 8px;
  font-size: 20px;
  color: #fff;
  line-height: 1.4;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
}
.slide-summary {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.86);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.dots {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 2;
  display: flex;
  gap: 6px;
}
.dots button {
  width: 8px;
  height: 6px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: all 0.3s ease;
}
.dots button.active {
  width: 20px;
  background: #fff;
}
</style>
