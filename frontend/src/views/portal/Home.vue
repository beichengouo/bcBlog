<template>
  <div class="home">
    <!-- 首屏 Hero -->
    <section class="hero">
      <div class="hero-inner">
        <p class="hero-kicker">Welcome to</p>
        <h1 class="hero-title">{{ siteName }}</h1>
        <div class="hero-sub">
          <span class="typed">{{ typedText }}</span>
          <span class="caret"></span>
        </div>
        <div class="hero-scroll" @click="scrollToArticles">
          <span>向下探索</span>
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M19 12l-7 7-7-7" />
          </svg>
        </div>
      </div>
    </section>

    <div id="articles" class="container">
      <aside class="sidebar">
        <AnnouncementBoard />
        <div class="side-card glass">
          <div class="side-title">分类</div>
          <el-tree
            :data="categories"
            :props="catProps"
            node-key="id"
            :default-expand-all="true"
            :current-node-key="currentCategory"
            highlight-current
            class="cat-tree"
            @node-click="onCategoryClick"
          >
            <template #default="{ data }">
              <span>{{ data.name }}</span>
            </template>
          </el-tree>
          <div v-if="currentCategory" class="clear-filter" @click="clearFilter">清除筛选</div>
        </div>

        <div class="side-card glass">
          <div class="side-title">标签</div>
          <div class="tag-cloud">
            <span
              v-for="t in tags"
              :key="t.id"
              class="tag-chip"
              :class="{ active: currentTag === t.id }"
              @click="onTagClick(t)"
            >{{ t.name }}</span>
          </div>
        </div>
      </aside>

      <section class="content">
        <div class="content-head">
          <div class="switch">
            <button :class="{ active: mode === 'masonry' }" @click="mode = 'masonry'">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="10" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="17" width="7" height="4" rx="1" />
              </svg>
              瀑布流
            </button>
            <button :class="{ active: mode === 'timeline' }" @click="mode = 'timeline'">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 7v5l3 3" />
              </svg>
              时间线
            </button>
          </div>
        </div>
        <ArticleList :params="listParams" :mode="mode" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { portalCategoryTree } from '@/api/category'
import { portalTagList } from '@/api/tag'
import { useSiteStore } from '@/store/site'
import ArticleList from '@/components/portal/ArticleList.vue'
import AnnouncementBoard from '@/components/portal/AnnouncementBoard.vue'

const route = useRoute()
const router = useRouter()
const siteStore = useSiteStore()

const categories = ref([])
const tags = ref([])
const catProps = { label: 'name', children: 'children' }
const mode = ref('masonry')
const siteName = ref('bcBlog')

const typedText = ref('')
const phrases = ref([])
let typeTimer = 0
let phraseIdx = 0
let charIdx = 0
let deleting = false
let snapLocked = false

const currentCategory = computed(() => (route.query.category ? Number(route.query.category) : null))
const currentTag = computed(() => (route.query.tag ? Number(route.query.tag) : null))
const currentKeyword = computed(() => route.query.keyword || '')

const listParams = computed(() => {
  const params = {}
  if (currentCategory.value) params.categoryId = currentCategory.value
  if (currentTag.value) params.tagId = currentTag.value
  if (currentKeyword.value) params.keyword = currentKeyword.value
  return params
})

function onCategoryClick(node) {
  router.push({ path: '/portal', query: { category: node.id } })
}

function onTagClick(tag) {
  router.push({ path: '/portal', query: { tag: tag.id } })
}

function clearFilter() {
  router.push({ path: '/portal' })
}

// 点击向下箭头，直接平滑滚动到文章展示区
function scrollToArticles() {
  const el = document.getElementById('articles')
  if (!el) return
  const headerH = document.querySelector('.portal-header')?.offsetHeight || 64
  const targetY = el.getBoundingClientRect().top + window.scrollY - headerH - 20
  const startY = window.scrollY
  const diff = targetY - startY
  const duration = Math.min(900, Math.max(450, Math.abs(diff) * 0.45))
  const docEl = document.documentElement
  const prev = docEl.style.scrollBehavior
  docEl.style.scrollBehavior = 'auto'
  const start = performance.now()
  const ease = (t) => (t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t)
  function step(now) {
    const p = Math.min(1, (now - start) / duration)
    window.scrollTo(0, startY + diff * ease(p))
    if (p < 1) {
      requestAnimationFrame(step)
    } else {
      docEl.style.scrollBehavior = prev
    }
  }
  requestAnimationFrame(step)
}

// 首页顶部向下滚动时，直接平滑滚到文章区，而不是一步步滚动
function onWheel(e) {
  if (e.deltaY <= 0) return
  if (window.scrollY > 10) return
  e.preventDefault()
  if (snapLocked) return
  snapLocked = true
  scrollToArticles()
  window.setTimeout(() => {
    snapLocked = false
  }, 900)
}

// 打字机效果：轮流打出站点描述 / 关键词
function type() {
  if (!phrases.value.length) return
  const current = phrases.value[phraseIdx]
  if (!deleting) {
    charIdx++
    typedText.value = current.slice(0, charIdx)
    if (charIdx >= current.length) {
      deleting = true
      typeTimer = window.setTimeout(type, 1600)
      return
    }
    typeTimer = window.setTimeout(type, 70)
  } else {
    charIdx--
    typedText.value = current.slice(0, charIdx)
    if (charIdx <= 0) {
      deleting = false
      phraseIdx = (phraseIdx + 1) % phrases.value.length
      typeTimer = window.setTimeout(type, 400)
      return
    }
    typeTimer = window.setTimeout(type, 35)
  }
}

onMounted(async () => {
  window.addEventListener('wheel', onWheel, { passive: false })
  const config = await siteStore.load()
  siteName.value = config.siteName || 'bcBlog'

  const arr = []
  if (config.siteSlogan) arr.push(config.siteSlogan)
  if (config.siteDescription && config.siteDescription !== config.siteSlogan) arr.push(config.siteDescription)
  if (config.siteKeywords) {
    config.siteKeywords.split(/[,，]/).filter(Boolean).forEach((k) => arr.push('#' + k.trim()))
  }
  if (!arr.length) arr.push('愿每一次点击都有温度')
  phrases.value = arr
  type()

  categories.value = await portalCategoryTree()
  tags.value = await portalTagList()
})

onUnmounted(() => {
  clearTimeout(typeTimer)
  window.removeEventListener('wheel', onWheel)
})
</script>

<style scoped>
.home {
  position: relative;
}

/* Hero 首屏 */
.hero {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--header-height) 20px 40px;
  text-align: center;
}
.hero-inner {
  max-width: 720px;
}
.hero-kicker {
  margin: 0 0 10px;
  font-size: 14px;
  letter-spacing: 4px;
  color: var(--text-muted);
  text-transform: uppercase;
}
.hero-title {
  margin: 0;
  font-size: clamp(40px, 8vw, 72px);
  font-weight: 800;
  line-height: 1.1;
  background: linear-gradient(120deg, var(--accent), var(--accent-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.hero-sub {
  margin-top: 22px;
  min-height: 28px;
  font-size: 17px;
  color: var(--text-muted);
}
.caret {
  display: inline-block;
  width: 2px;
  height: 1.1em;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--accent);
  animation: blink 1s steps(1) infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.hero-scroll {
  margin-top: 44px;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  animation: float 2.2s ease-in-out infinite;
}
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(8px); }
}

/* 主内容 */
.container {
  max-width: 1180px;
  margin: 0 auto;
  padding: 20px 20px 40px;
  display: flex;
  gap: 24px;
  align-items: flex-start;
  scroll-margin-top: calc(var(--header-height) + 20px);
}
.sidebar {
  width: 240px;
  flex-shrink: 0;
}
.side-card {
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px;
  margin-bottom: 18px;
}
.side-title {
  font-weight: 700;
  color: var(--text-strong);
  margin-bottom: 12px;
}
.cat-tree {
  background: transparent;
}
.cat-tree :deep(.el-tree-node__content) {
  background: transparent;
  color: var(--text);
  border-radius: 8px;
  height: 34px;
}
.cat-tree :deep(.el-tree-node__content:hover) {
  background: var(--accent-soft);
}
.cat-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--accent-soft);
  color: var(--accent);
}
.clear-filter {
  margin-top: 12px;
  font-size: 13px;
  color: var(--accent);
  cursor: pointer;
}
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag-chip {
  padding: 5px 12px;
  font-size: 13px;
  border-radius: 999px;
  color: var(--text);
  background: var(--accent-soft);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
}
.tag-chip:hover {
  border-color: var(--accent);
}
.tag-chip.active {
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.content {
  flex: 1;
  min-width: 0;
}
.content-head {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
}
.switch {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 999px;
  background: var(--glass-bg);
  border: 1px solid var(--border);
  backdrop-filter: blur(12px);
}
.switch button {
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  padding: 7px 14px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.25s ease;
}
.switch button.active {
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: var(--shadow);
}

@media (max-width: 860px) {
  .container {
    flex-direction: column;
  }
  .sidebar {
    width: 100%;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 18px;
  }
  .side-card {
    margin-bottom: 0;
  }
}
@media (max-width: 560px) {
  .sidebar {
    grid-template-columns: 1fr;
  }
}
</style>
