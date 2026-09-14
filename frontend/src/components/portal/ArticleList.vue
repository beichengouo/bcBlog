<template>
  <div class="article-list">
    <!-- 卡片瀑布流 -->
    <div v-if="mode === 'masonry'" class="masonry">
      <article
        v-for="(a, i) in list"
        :key="a.id"
        :ref="(el) => setCardRef(el, i)"
        class="card reveal"
        :class="{ in: revealed[i] }"
        :style="{ '--d': (i % 8) * 0.05 + 's' }"
        @click="go(a.id)"
      >
        <div class="cover">
          <img v-if="a.cover" :src="a.cover" loading="lazy" alt="" />
          <div v-else class="cover-fallback">
            <svg viewBox="0 0 24 24" width="42" height="42" fill="none" stroke="currentColor" stroke-width="1.4">
              <path d="M4 4h16v16H4z" />
              <circle cx="8.5" cy="8.5" r="1.5" />
              <path d="M21 15l-5-5L5 21" />
            </svg>
          </div>
          <span v-if="a.isTop === 1" class="badge">置顶</span>
        </div>
        <div class="body">
          <h3 class="title">{{ a.title }}</h3>
          <p v-if="a.summary" class="summary">{{ a.summary }}</p>
          <div class="meta">
            <span>{{ shortDate(a.createTime) }} · {{ a.authorName || '管理员' }}</span>
            <span>浏览 {{ a.viewCount || 0 }}</span>
          </div>
        </div>
      </article>
    </div>

    <!-- 时间线 -->
    <div v-else class="timeline">
      <div
        v-for="(a, i) in list"
        :key="a.id"
        :ref="(el) => setCardRef(el, i)"
        class="timeline-item reveal"
        :class="{ in: revealed[i] }"
        :style="{ '--d': (i % 6) * 0.06 + 's' }"
      >
        <div class="timeline-date">
          <span class="day">{{ datePart(a.createTime) }}</span>
          <span class="ym">{{ yearMonth(a.createTime) }}</span>
        </div>
        <div class="timeline-dot"></div>
        <article class="card" @click="go(a.id)">
          <div class="body">
            <h3 class="title">{{ a.title }}</h3>
            <p v-if="a.summary" class="summary">{{ a.summary }}</p>
            <div class="meta">
              <span>{{ a.authorName || '管理员' }}</span>
              <span>浏览 {{ a.viewCount || 0 }}</span>
              <span v-if="a.isTop === 1" class="top">置顶</span>
            </div>
          </div>
        </article>
      </div>
    </div>

    <el-empty v-if="!loading && !list.length" description="暂无文章" />
    <el-pagination
      v-if="total > size"
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="prev, pager, next"
      class="pager"
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { listPortalArticles } from '@/api/article'

const props = defineProps({
  params: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'masonry' } // masonry | timeline
})

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const revealed = ref([])
const cardRefs = ref([])
let observer = null

function setCardRef(el, i) {
  if (el) cardRefs.value[i] = el
}

function observeCards() {
  if (observer) observer.disconnect()
  if (!('IntersectionObserver' in window)) {
    revealed.value = list.value.map(() => true)
    return
  }
  observer = new IntersectionObserver((entries) => {
    for (const e of entries) {
      if (e.isIntersecting) {
        const idx = Number(e.target.dataset.index)
        revealed.value[idx] = true
        observer.unobserve(e.target)
      }
    }
  }, { threshold: 0.12 })
  cardRefs.value.forEach((el, i) => {
    if (el) {
      el.dataset.index = String(i)
      observer.observe(el)
    }
  })
}

async function load() {
  loading.value = true
  try {
    const data = await listPortalArticles({ page: page.value, size: size.value, ...props.params })
    list.value = data.list
    total.value = data.total
    revealed.value = data.list.map(() => false)
    cardRefs.value = []
    await nextTick()
    observeCards()
  } finally {
    loading.value = false
  }
}

function go(id) {
  router.push(`/portal/article/${id}`)
}

function shortDate(dt) {
  return dt ? String(dt).slice(0, 10) : ''
}
function datePart(dt) {
  return dt ? String(dt).slice(8, 10) : ''
}
function yearMonth(dt) {
  return dt ? String(dt).slice(0, 7) : ''
}
function timePart(dt) {
  return dt ? String(dt).slice(11, 16) : ''
}

watch(() => props.params, () => {
  page.value = 1
  load()
}, { deep: true })

watch(() => props.mode, async () => {
  revealed.value = list.value.map(() => false)
  cardRefs.value = []
  await nextTick()
  observeCards()
})

onMounted(load)
onUnmounted(() => observer && observer.disconnect())
</script>

<style scoped>
.masonry {
  column-count: 3;
  column-gap: 20px;
}
.card {
  break-inside: avoid;
  margin-bottom: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  backdrop-filter: blur(12px);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.35s ease, box-shadow 0.35s ease, background 0.35s ease;
}
.card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-hover);
}
.cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: linear-gradient(135deg, var(--accent-soft), transparent);
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}
.card:hover .cover img {
  transform: scale(1.08);
}
.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  background:
    radial-gradient(circle at 20% 20%, var(--accent-soft), transparent 60%),
    radial-gradient(circle at 80% 80%, var(--accent-soft), transparent 60%);
}
.badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 9px;
  font-size: 12px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  border-radius: 999px;
}
.body {
  padding: 16px;
}
.title {
  margin: 0 0 8px;
  font-size: 17px;
  line-height: 1.4;
  color: var(--text-strong);
  transition: color 0.2s ease;
}
.card:hover .title {
  color: var(--accent);
}
.summary {
  margin: 0 0 12px;
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-muted);
  font-size: 12px;
}

/* 时间线 */
.timeline {
  position: relative;
  padding-left: 18px;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: linear-gradient(var(--accent), var(--accent-2));
  border-radius: 2px;
  opacity: 0.5;
}
.timeline-item {
  position: relative;
  display: grid;
  grid-template-columns: 86px 1fr;
  gap: 18px;
  margin-bottom: 26px;
}
.timeline-date {
  text-align: right;
  padding-top: 10px;
}
.timeline-date .day {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: var(--accent);
  line-height: 1;
}
.timeline-date .ym {
  font-size: 12px;
  color: var(--text-muted);
}
.timeline-dot {
  position: absolute;
  left: 0;
  top: 16px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--card-solid);
  border: 3px solid var(--accent);
  box-shadow: 0 0 0 4px var(--accent-soft);
}
.timeline .card {
  margin-bottom: 0;
}
.timeline .top {
  color: var(--accent);
}

/* 入场动画 */
.reveal {
  opacity: 0;
  transform: translateY(24px);
  transition: opacity 0.5s ease, transform 0.5s ease;
  transition-delay: var(--d, 0s);
}
.reveal.in {
  opacity: 1;
  transform: translateY(0);
}

.pager {
  margin-top: 8px;
  justify-content: center;
}

@media (max-width: 1200px) {
  .masonry {
    column-count: 2;
  }
}
@media (max-width: 640px) {
  .masonry {
    column-count: 1;
  }
  .timeline-item {
    grid-template-columns: 64px 1fr;
    gap: 12px;
  }
  .timeline-date .day {
    font-size: 22px;
  }
}
</style>
