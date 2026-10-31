<template>
  <div class="article-list" :aria-busy="loading">
    <div v-if="loading" class="article-skeletons" role="status" aria-label="正在加载文章">
      <div v-for="n in 4" :key="n" class="article-skeleton"><div></div><span></span><span></span><span></span></div>
    </div>
    <div v-else-if="loadError" class="load-error glass" role="alert">
      <strong>文章暂时没能加载出来</strong><p>请检查网络连接，稍后再试一次。</p><button type="button" @click="load">重新加载</button>
    </div>
    <!-- 卡片瀑布流 -->
    <div v-else-if="mode === 'masonry'" class="masonry">
      <article
        v-for="(a, i) in list"
        :key="a.id"
        :ref="(el) => setCardRef(el, i)"
        class="card reveal"
        :class="{ in: revealed[i], 'card-featured': i === 0 }"
        :style="{ '--reveal-delay': (revealDelays[i] || 0) + 'ms' }"
        @focusin="revealCard(i)"
      >
        <div class="cover">
          <img v-if="a.cover && !failedCovers.has(a.cover)" :src="a.cover" loading="lazy" alt="" @error="failedCovers.add(a.cover)" />
          <div v-else class="cover-fallback" :class="'cover-tone-' + (i % 3)" aria-hidden="true">
            <span class="cover-issue">NOTES &amp; MOMENTS</span>
            <span class="cover-letter">{{ (a.title || '记').slice(0, 1) }}</span>
            <span class="cover-footnote">每一篇文字，都有它的温度。<span>↗</span></span>
          </div>
          <span v-if="a.isTop === 1" class="badge">置顶</span>
        </div>
        <div class="body">
          <div class="article-kicker"><span>JOURNAL / {{ String((page - 1) * size + i + 1).padStart(2, '0') }}</span><time>{{ shortDate(a.createTime) }}</time></div>
          <h3 class="title"><router-link :to="`/portal/article/${a.id}`" class="article-link"><span v-if="a.aiGenerated === 1" class="ai-badge">AI</span>{{ a.title }}</router-link></h3>
          <p v-if="a.summary" class="summary">{{ a.summary }}</p>
          <div class="meta">
            <span class="author"><span class="author-mark" aria-hidden="true">{{ (a.authorName || '管理员').slice(0, 1) }}</span><span>{{ a.authorName || '管理员' }}</span></span>
            <span class="read-count">{{ a.viewCount || 0 }} 次阅读</span>
          </div>
          <div class="read-more" aria-hidden="true"><span>读一读这篇故事</span><span>↗</span></div>
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
        :style="{ '--reveal-delay': (revealDelays[i] || 0) + 'ms' }"
        @focusin="revealCard(i)"
      >
        <div class="timeline-date">
          <span class="day">{{ datePart(a.createTime) }}</span>
          <span class="ym">{{ yearMonth(a.createTime) }}</span>
        </div>
        <div class="timeline-dot"></div>
        <article class="card">
          <div class="body">
            <h3 class="title"><router-link :to="`/portal/article/${a.id}`" class="article-link">{{ a.title }}</router-link></h3>
            <p v-if="a.summary" class="summary">{{ a.summary }}</p>
            <div class="meta">
              <span>{{ a.authorName || '管理员' }}</span>
              <span>{{ a.viewCount || 0 }} 次阅读</span>
              <span v-if="a.isTop === 1" class="top">置顶</span>
              <span class="timeline-read" aria-hidden="true">继续阅读 ↗</span>
            </div>
          </div>
        </article>
      </div>
    </div>

    <el-empty v-if="!loading && !loadError && !list.length" :description="Object.keys(params).length ? '没有找到匹配的文章，换个关键词或筛选试试吧' : '故事正在酝酿，期待第一篇文章'" />
    <el-pagination
      v-if="!loading && !loadError && total > size"
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
import { listPortalArticles } from '@/api/article'

const props = defineProps({
  params: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'masonry' } // masonry | timeline
})

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const loadError = ref(false)
const revealed = ref([])
const revealDelays = ref([])
const cardRefs = ref([])
const failedCovers = ref(new Set())
let observer = null
let requestId = 0
let disposed = false

function setCardRef(el, i) {
  if (el) cardRefs.value[i] = el
}

function revealCard(index, delay = 0) {
  if (!Number.isInteger(index) || index < 0 || index >= list.value.length || revealed.value[index]) return
  revealDelays.value[index] = delay
  revealed.value[index] = true
}

function observeCards() {
  if (disposed || loading.value || loadError.value) return
  if (observer) observer.disconnect()
  observer = null
  if (!window.IntersectionObserver || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    revealed.value = list.value.map(() => true)
    return
  }
  const activeObserver = new window.IntersectionObserver((entries) => {
    if (disposed || observer !== activeObserver) return
    let order = 0
    for (const entry of entries) {
      if (!entry.isIntersecting) continue
      revealCard(Number(entry.target.dataset.index), Math.min(order++, 3) * 85)
      activeObserver.unobserve(entry.target)
    }
  }, { threshold: 0.08, rootMargin: '0px 0px -48px 0px' })
  observer = activeObserver
  cardRefs.value.forEach((el, i) => {
    if (el) {
      el.dataset.index = String(i)
      observer.observe(el)
    }
  })
}

async function load() {
  const id = ++requestId
  loading.value = true
  loadError.value = false
  if (observer) observer.disconnect()
  observer = null
  cardRefs.value = []
  try {
    const data = await listPortalArticles({ page: page.value, size: size.value, ...props.params })
    if (id !== requestId) return
    list.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total) || 0
    revealed.value = list.value.map(() => false)
    revealDelays.value = []
    // 先挂载卡片，再观察入场；加载态下卡片还不在 DOM 中。
    loading.value = false
    await nextTick()
    if (id !== requestId) return
    observeCards()
  } catch (e) {
    if (id === requestId) loadError.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
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

watch(() => props.params, () => {
  page.value = 1
  load()
}, { deep: true })

watch(() => props.mode, async () => {
  revealed.value = list.value.map(() => false)
  revealDelays.value = []
  cardRefs.value = []
  await nextTick()
  observeCards()
})

onMounted(load)
onUnmounted(() => {
  disposed = true
  requestId++
  if (observer) observer.disconnect()
})
</script>

<style scoped>/* 网站AI 生成的内容角标 */
.ai-badge {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
  vertical-align: middle;
  color: #fff;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.masonry {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  align-items: start;
}
.card {
  position: relative;
  break-inside: avoid;
  margin-bottom: 0;
  min-width: 0;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  backdrop-filter: blur(12px);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.35s ease, box-shadow 0.35s ease, background 0.35s ease;
}
.article-link::after { content: ''; position: absolute; inset: 0; z-index: 1; }
.card:focus-within { outline: 2px solid var(--accent); outline-offset: 4px; }
.article-skeletons { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; }
.article-skeleton { border: 1px solid var(--border); background: var(--card); border-radius: var(--radius); overflow: hidden; padding-bottom: 22px; }
.article-skeleton > div { aspect-ratio: 16/9; background: var(--accent-soft); }
.article-skeleton > span { display: block; height: 12px; margin: 18px 22px 0; border-radius: 6px; background: var(--accent-soft); }
.article-skeleton > span:last-child { width: 42%; }
.load-error { text-align: center; padding: 48px 24px; border-radius: var(--radius); }
.load-error strong { color: var(--text-strong); }
.load-error p { color: var(--text-muted); font-size: 14px; }
.load-error button { border: 1px solid var(--border); border-radius: 10px; padding: 10px 20px; background: var(--accent-soft); color: var(--text-strong); cursor: pointer; }
.card:hover {
  transform: translateY(-4px);
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
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  background: var(--accent-soft);
  overflow: hidden;
}
.cover-fallback::after { content: ''; position: absolute; width: 170px; height: 170px; border-radius: 50%; border: 1px solid currentColor; right: -50px; top: -50px; opacity: .12; box-shadow: 0 0 0 28px var(--accent-soft), 0 0 0 29px var(--border); }
.cover-tone-1 { color: var(--accent-2); background: color-mix(in srgb, var(--accent-2) 10%, var(--card-solid)); }
.cover-tone-2 { color: var(--text); background: color-mix(in srgb, var(--text-muted) 10%, var(--card-solid)); }
.cover-letter { font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif; font-size: 90px; font-weight: 400; line-height: 1; opacity: .65; transform: rotate(-8deg); }
.cover-issue { position: absolute; top: 22px; left: 24px; font-size: 8px; letter-spacing: 2px; opacity: .7; }
.cover-footnote { position: absolute; bottom: 20px; left: 24px; right: 24px; display: flex; justify-content: space-between; font-size: 10px; letter-spacing: 1px; opacity: .8; }
.article-kicker { display: flex; justify-content: space-between; gap: 8px; color: var(--text-muted); font-size: 9px; letter-spacing: 1.2px; margin-bottom: 14px; }
.article-kicker time { font-variant-numeric: tabular-nums; letter-spacing: .5px; }
.author { display: inline-flex; align-items: center; gap: 8px; min-width: 0; }
.author > span:last-child { max-width: 150px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.author-mark { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; width: 24px; height: 24px; border-radius: 50%; background: var(--accent-soft); color: var(--accent); font-size: 10px; }
.read-count { white-space: nowrap; font-size: 10px; }
.read-more { display: flex; justify-content: space-between; margin-top: 18px; font-size: 11px; color: var(--accent); }
.read-more > span:last-child { font-size: 17px; }
.timeline-read { margin-left: auto; color: var(--accent); }
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
  padding: 22px;
  min-width: 0;
}
.title {
  margin: 0 0 8px;
  font-size: 19px;
  line-height: 1.6;
  overflow-wrap: anywhere;
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
  line-height: 1.9;
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
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 14px;
  border-top: 1px solid var(--border);
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
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 18px;
  margin-bottom: 26px;
}
.timeline-date {
  text-align: right;
  padding: 18px 14px 0 0;
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
  left: -18px;
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
  translate: 0 40px;
}
.reveal.in {
  animation: article-enter 760ms cubic-bezier(.2, .7, .2, 1) var(--reveal-delay, 0ms) both;
}
@keyframes article-enter {
  from { opacity: 0; translate: 0 40px; }
  to { opacity: 1; translate: 0 0; }
}
@media (prefers-reduced-motion: reduce) {
  .reveal, .reveal.in { opacity: 1; translate: none; animation: none; }
}

.pager {
  margin-top: 8px;
  justify-content: center;
}

@media (max-width: 1200px) {
  .masonry { gap: 20px; }
}
@media (min-width: 961px) {
  .masonry .card-featured { grid-column: 1 / -1; display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr); }
  .card-featured .cover { aspect-ratio: auto; min-height: 320px; }
  .card-featured .cover img { position: absolute; inset: 0; }
  .card-featured .cover-fallback { min-height: 320px; }
  .card-featured .cover-letter { font-size: 120px; }
  .card-featured .body { display: flex; flex-direction: column; justify-content: center; padding: 28px; }
  .card-featured .title { font-size: 23px; }
  .card-featured .summary { -webkit-line-clamp: 4; }
}
@media (max-width: 640px) {
  .article-skeletons { grid-template-columns: 1fr; }
  .masonry {
    grid-template-columns: minmax(0, 1fr);
  }
  .timeline-item {
    grid-template-columns: 54px minmax(0, 1fr);
    gap: 12px;
  }
  .timeline-date .day {
    font-size: 22px;
  }
  .body { padding: 20px; }
  .title { font-size: 18px; }
  .timeline .body { padding: 18px 16px; }
  .timeline-date .ym { font-size: 9px; }
  .timeline-date { padding-right: 2px; }
  .timeline { padding-left: 12px; }
  .timeline-dot { left: -12px; width: 10px; height: 10px; }
  .timeline::before { left: 4px; }
}
</style>
