<template>
  <div class="home">
    <!-- 首屏 Hero -->
    <section class="hero">
      <div class="hero-inner">
        <div class="hero-copy">
        <p class="hero-kicker"><span></span> A LITTLE SPACE FOR IDEAS</p>
        <h1 class="hero-title">{{ siteName }}</h1>
        <figure class="hero-sub daily-quote" :aria-busy="quoteLoading">
          <blockquote>{{ quoteLoading ? '正在拾取今天的一句话…' : dailyQuote.text }}</blockquote>
          <figcaption v-if="!quoteLoading">— {{ dailyQuote.source }}</figcaption>
        </figure>
        <p class="hero-description">把灵感写成文字，把日常收藏成光。<br />在这里，慢慢记录，也慢慢遇见。</p>
        <div class="hero-actions">
        <button type="button" class="hero-scroll" @click="scrollToArticles">
          <span>开始阅读</span>
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M19 12l-7 7-7-7" />
          </svg>
        </button>
        <router-link to="/portal/photos" class="hero-secondary">去看看流光忆庭 <span aria-hidden="true">↗</span></router-link>
        </div>
        <p class="hero-note">文字有迹，热爱有声。</p>
        </div>
        <div class="hero-directory">
          <div class="directory-heading"><span>随心漫游</span><span class="directory-label">EXPLORE / 01—03</span></div>
          <router-link to="/portal/photos" class="directory-link">
            <span class="directory-number">01</span><div><strong>流光忆庭</strong><span>让值得记住的瞬间，留在这里</span></div><span class="directory-arrow" aria-hidden="true">↗</span>
          </router-link>
          <router-link to="/portal/resources" class="directory-link">
            <span class="directory-number">02</span><div><strong>智库</strong><span>收集好用的工具与闪光的想法</span></div><span class="directory-arrow" aria-hidden="true">↗</span>
          </router-link>
          <router-link to="/portal/sandbox" class="directory-link">
            <span class="directory-number">03</span><div><strong>沙盒世界</strong><span>推开另一扇门，发现新的故事</span></div><span class="directory-arrow" aria-hidden="true">↗</span>
          </router-link>
          <div class="directory-footer"><span class="directory-star" aria-hidden="true">✳</span> 保持好奇，故事还在继续。</div>
        </div>
      </div>
    </section>

    <!-- 首页中段：最新文章轮播 + 音乐播放器 & 歌词 -->
    <section v-if="showShowcase" id="showcase" class="showcase">
      <div class="showcase-inner">
        <div class="showcase-carousel">
          <div class="showcase-cover"><LatestPostsCarousel :count="carouselCount" /></div>
          <section class="showcase-discover glass" aria-label="小站探索">
            <header><span>读完这篇，再逛一会儿</span><small>DISCOVER MORE</small></header>
            <div class="discover-links">
              <router-link to="/portal/iris"><span class="discover-symbol" aria-hidden="true">✧</span><strong>认识 IRIS <span>↗</span></strong><p>认识住在小站里的伊莉丝</p></router-link>
              <router-link to="/portal/games/pixelrogue/codex"><span class="discover-symbol" aria-hidden="true">♧</span><strong>星途图鉴 <span>↗</span></strong><p>翻阅装备、遗物与命运牌</p></router-link>
              <router-link to="/portal/announcements"><span class="discover-symbol" aria-hidden="true">✉</span><strong>小站近况 <span>↗</span></strong><p>看看最近的更新与公告</p></router-link>
            </div>
          </section>
        </div>
        <div class="showcase-player">
          <HomeMusicPlayer />
        </div>
      </div>
    </section>

    <GameFeature />

    <!-- 最近评论：原生评论取自本站，Gitalk 模式取自 GitHub Issues -->
    <section v-if="recentComments.length" class="recent-comments">
      <div class="rc-container glass">
        <button class="rc-header" :class="{ open: commentsOpen }" @click="commentsOpen = !commentsOpen" :aria-expanded="commentsOpen">
          <span class="rc-head-left">
            <span class="rc-title">最近评论</span>
            <span class="rc-badge">{{ recentComments.length }}</span>
          </span>
          <span class="rc-head-right">
            <span class="rc-sub">{{ commentSystem === 'native' ? '本站评论' : 'Gitalk · GitHub' }}</span>
            <svg class="rc-arrow" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m6 9 6 6 6-6" />
            </svg>
          </span>
        </button>
        <div class="rc-drop" :class="{ open: commentsOpen }">
          <div v-for="c in recentComments" :key="c.id" class="rc-item" @click="goComment(c)">
            <img class="rc-avatar" :src="c.avatar" alt="" loading="lazy" />
            <div class="rc-item-content">
              <div class="rc-meta">
                <span class="rc-author">{{ c.author }}</span>
                <span class="rc-time">{{ formatCommentTime(c.createdAt) }}</span>
              </div>
              <p class="rc-text">{{ c.body }}</p>
              <div v-if="c.pageTitle" class="rc-page">评论于《{{ c.pageTitle }}》</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div id="articles" class="container">
      <aside class="sidebar" aria-label="公告与文章筛选">
        <AnnouncementBoard />
        <section class="side-card glass category-card" aria-labelledby="category-heading">
          <div class="side-heading"><span class="side-icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M3 6h7l2 3h9v11H3z M3 6V4h7l2 2h9v3" /></svg></span><div><span class="side-kicker">CATEGORIES</span><h2 id="category-heading">文章分类</h2></div><span v-if="!filtersLoading && !categoryLoadError" class="side-count">{{ categoryCount }}</span></div>
          <p class="side-description">按主题，找到想读的内容。</p>
          <div v-if="filtersLoading" class="filter-placeholder" role="status">分类整理中…</div>
          <div v-else-if="categoryLoadError" class="filter-placeholder" role="alert">分类暂时无法加载 <button type="button" @click="loadFilters">重试</button></div>
          <el-tree
            v-else-if="categories.length"
            ref="categoryTreeRef"
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
              <span class="category-label" :title="data.name">{{ data.name }}</span>
            </template>
          </el-tree>
          <p v-else class="filter-placeholder">分类还在整理，先随心读读吧。</p>
          <button v-if="currentCategory" type="button" class="clear-filter" @click="clearQueryFilter('category')">清除分类筛选 <span aria-hidden="true">×</span></button>
        </section>

        <section class="side-card glass tags-card" aria-labelledby="tags-heading">
          <div class="side-heading"><span class="side-icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M3 3h8l10 10-8 8L3 11z" /><circle cx="7.5" cy="7.5" r="1" /></svg></span><div><span class="side-kicker">TOPICS</span><h2 id="tags-heading">灵感标签</h2></div><span v-if="!filtersLoading && !tagLoadError" class="side-count">{{ tags.length }}</span></div>
          <p class="side-description">跟着一个关键词，继续探索。</p>
          <div v-if="filtersLoading" class="filter-placeholder" role="status">标签整理中…</div>
          <div v-else-if="tagLoadError" class="filter-placeholder" role="alert">标签暂时无法加载 <button type="button" @click="loadFilters">重试</button></div>
          <div v-else-if="tags.length" id="home-tag-cloud" class="tag-cloud">
            <button
              v-for="t in visibleTags"
              :key="t.id"
              type="button"
              class="tag-chip"
              :class="{ active: currentTag === t.id }"
              :aria-pressed="currentTag === t.id"
              @click="onTagClick(t)"
              :title="t.name"
            ><span class="tag-hash" aria-hidden="true">#</span>{{ t.name }}</button>
          </div>
          <p v-else class="filter-placeholder">还没有标签，灵感正在路上。</p>
          <div v-if="!filtersLoading && !tagLoadError && (tags.length > 12 || currentTag)" class="filter-footer">
            <button v-if="tags.length > 12" type="button" class="tags-expand" :aria-expanded="tagsExpanded" aria-controls="home-tag-cloud" @click="tagsExpanded = !tagsExpanded">{{ tagsExpanded ? '收起标签' : '查看全部标签' }} <span aria-hidden="true">{{ tagsExpanded ? '−' : '+' }}</span></button>
            <button v-if="currentTag" type="button" class="clear-tag" @click="clearQueryFilter('tag')">清除选择 ×</button>
          </div>
        </section>
      </aside>

      <section class="content">
        <div class="content-head">
          <div class="section-heading"><p>THE JOURNAL</p><h2>{{ currentKeyword ? '搜索结果' : (currentCategory || currentTag ? '筛选文章' : '最近更新') }}</h2><span v-if="currentKeyword" class="search-context">关于「{{ currentKeyword }}」</span></div>
          <div class="switch" role="group" aria-label="文章展示方式">
            <button :class="{ active: mode === 'masonry' }" :aria-pressed="mode === 'masonry'" @click="mode = 'masonry'">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="10" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="17" width="7" height="4" rx="1" />
              </svg>
              卡片
            </button>
            <button :class="{ active: mode === 'timeline' }" :aria-pressed="mode === 'timeline'" @click="mode = 'timeline'">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 7v5l3 3" />
              </svg>
              时间线
            </button>
          </div>
        </div>
        <div class="journal-context">
          <span>一些记录，一些思考，还有正在发生的故事。</span>
          <button v-if="currentCategory || currentTag || currentKeyword" type="button" @click="clearFilter">查看全部文章 <span aria-hidden="true">↗</span></button>
        </div>
        <ArticleList :params="listParams" :mode="mode" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { portalCategoryTree } from '@/api/category'
import { portalTagList } from '@/api/tag'
import { getRecentGitalkComments } from '@/api/gitalk'
import { recentComments as recentNativeComments } from '@/api/comment'
import { useSiteStore } from '@/store/site'
import ArticleList from '@/components/portal/ArticleList.vue'
import AnnouncementBoard from '@/components/portal/AnnouncementBoard.vue'
import LatestPostsCarousel from '@/components/portal/LatestPostsCarousel.vue'
import HomeMusicPlayer from '@/components/portal/HomeMusicPlayer.vue'
import GameFeature from '@/components/portal/GameFeature.vue'
import { getDailyQuote, nextQuoteRefresh, shanghaiDay } from '@/utils/dailyQuote'

const route = useRoute()
const router = useRouter()
const siteStore = useSiteStore()

const categories = ref([])
const categoryTreeRef = ref(null)
const tags = ref([])
const filtersLoading = ref(true)
const categoryLoadError = ref(false)
const tagLoadError = ref(false)
const tagsExpanded = ref(false)
const categoryCount = computed(() => {
  const count = (items) => items.reduce((sum, item) => sum + 1 + count(item.children || []), 0)
  return count(categories.value)
})
const catProps = { label: 'name', children: 'children' }
const mode = ref('masonry')
const siteName = ref('bcBlog')
const showShowcase = ref(true)
const carouselCount = ref(5)
const recentComments = ref([])
// 当前站点的评论系统：native（本站评论）/ gitalk（GitHub Issues）
const commentSystem = ref('gitalk')
const commentsOpen = ref(false)

const dailyQuote = ref({ text: '', source: '', day: '', stale: false })
const quoteLoading = ref(true)
let quoteCategories = 'd,i,k'
let quoteTimer = 0
let disposed = false

const currentCategory = computed(() => (route.query.category ? Number(route.query.category) : null))
const currentTag = computed(() => (route.query.tag ? Number(route.query.tag) : null))
const currentKeyword = computed(() => route.query.keyword || '')
watch(currentCategory, async (value) => {
  await nextTick()
  if (!disposed) categoryTreeRef.value?.setCurrentKey(value)
})
const visibleTags = computed(() => {
  if (tagsExpanded.value) return tags.value
  const first = tags.value.slice(0, 12)
  const selected = tags.value.find((tag) => tag.id === currentTag.value)
  if (selected && !first.includes(selected)) first.push(selected)
  return first
})

const listParams = computed(() => {
  const params = {}
  if (currentCategory.value) params.categoryId = currentCategory.value
  if (currentTag.value) params.tagId = currentTag.value
  if (currentKeyword.value) params.keyword = currentKeyword.value
  return params
})

function onCategoryClick(node) {
  router.push({ path: '/portal', query: { ...route.query, category: node.id } })
}

function onTagClick(tag) {
  router.push({ path: '/portal', query: { ...route.query, tag: currentTag.value === tag.id ? undefined : tag.id } })
}

function clearQueryFilter(key) {
  const query = { ...route.query }
  delete query[key]
  router.push({ path: '/portal', query })
}

async function loadFilters() {
  filtersLoading.value = true
  const [categoryResult, tagResult] = await Promise.allSettled([portalCategoryTree(), portalTagList()])
  if (disposed) return
  categoryLoadError.value = categoryResult.status === 'rejected'
  tagLoadError.value = tagResult.status === 'rejected'
  categories.value = categoryLoadError.value ? [] : categoryResult.value || []
  tags.value = tagLoadError.value ? [] : tagResult.value || []
  filtersLoading.value = false
}

function clearFilter() {
  router.push({ path: '/portal' })
}

function formatCommentTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function goComment(c) {
  // 原生评论：直接跳到那篇文章的评论区
  if (c.articleId) {
    router.push('/portal/article/' + c.articleId)
    return
  }
  if (c.pagePath && c.pagePath.startsWith('/')) {
    router.push(c.pagePath)
  } else if (c.htmlUrl) {
    window.open(c.htmlUrl, '_blank')
  }
}

// 点击向下箭头，直接平滑滚动到文章展示区
function scrollToArticles() {
  const el = document.getElementById('articles')
  if (!el) return
  el.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
}

async function refreshDailyQuote() {
  quoteLoading.value = true
  const quote = await getDailyQuote(quoteCategories)
  if (disposed) return
  dailyQuote.value = quote
  quoteLoading.value = false
  clearTimeout(quoteTimer)
  quoteTimer = window.setTimeout(refreshDailyQuote, nextQuoteRefresh())
}
function checkQuoteDay() {
  if (document.visibilityState === 'visible' && dailyQuote.value.day !== shanghaiDay()) refreshDailyQuote()
}

onMounted(async () => {
  const config = await siteStore.load()
  if (disposed) return
  siteName.value = config.siteName || 'bcBlog'
  showShowcase.value = config.homeCarouselEnabled !== 0
  carouselCount.value = config.homeCarouselCount || 5

  quoteCategories = config.hitokotoCategories || 'd,i,k'
  refreshDailyQuote()
  document.addEventListener('visibilitychange', checkQuoteDay)

  await loadFilters()
  if (disposed) return
  // 「最近评论」跟着后台的评论系统走：原生评论就读本站评论，Gitalk 才去读 GitHub Issues
  commentSystem.value = config.commentSystem || 'gitalk'
  try {
    if (commentSystem.value === 'native') {
      const list = (await recentNativeComments(10)) || []
      recentComments.value = list.map((c) => ({
        id: c.id,
        articleId: c.articleId,
        avatar: c.avatar || '/uploads/logo/avatar.png',
        author: c.nickname || '游客',
        createdAt: c.createTime,
        body: c.content,
        pageTitle: c.articleTitle
      }))
    } else {
      recentComments.value = await getRecentGitalkComments(10)
    }
  } catch (e) {
    recentComments.value = []
  }
})

onUnmounted(() => {
  disposed = true
  clearTimeout(quoteTimer)
  document.removeEventListener('visibilitychange', checkQuoteDay)
})
</script>

<style scoped>
.home {
  position: relative;
}

/* Hero 首屏 */
.hero {
  min-height: min(800px, 85svh);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(var(--header-height) + 88px) 28px 80px;
}
.hero-inner {
  width: 100%;
  max-width: 1224px;
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: clamp(36px, 7vw, 110px);
  align-items: center;
}
.hero-copy { padding: clamp(22px, 3vw, 36px); border-radius: 24px; background: var(--card); border: 1px solid var(--glass-border); backdrop-filter: blur(16px); }
.hero-kicker {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 28px;
  font-size: 11px;
  letter-spacing: 2.6px;
  color: var(--text-muted);
  text-transform: uppercase;
}
.hero-kicker > span { width: 26px; height: 2px; background: var(--accent); }
.hero-title {
  margin: 0;
  font-size: clamp(44px, 6vw, 80px);
  font-weight: 800;
  line-height: 1.2;
  overflow-wrap: anywhere;
  letter-spacing: -2px;
  background: linear-gradient(120deg, var(--accent), var(--accent-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.hero-sub {
  margin-top: 18px;
  min-height: 30px;
  font-size: 17px;
  color: var(--text);
}
.daily-quote { margin-left: 0; margin-right: 0; max-width: 580px; padding: 20px 24px; border: 1px solid var(--glass-border); border-left: 3px solid var(--accent); border-radius: 0 16px 16px 0; background: var(--card-solid); box-shadow: var(--shadow); }
.daily-quote blockquote { margin: 0; font-size: clamp(17px, 1.6vw, 21px); font-weight: 500; line-height: 1.9; color: var(--text-strong); overflow-wrap: anywhere; }
.daily-quote figcaption { margin-top: 13px; font-size: 13px; line-height: 1.8; color: var(--text); }
.hero-description { margin: 22px 0 30px; font-size: 15px; line-height: 2; color: var(--text-muted); }
.hero-actions { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }
.hero-secondary { font-size: 13px; color: var(--text); }
.hero-secondary span { margin-left: 8px; color: var(--accent); }
.hero-secondary:hover { color: var(--accent); }
.hero-note { margin: 26px 0 0; color: var(--text-muted); font-size: 11px; letter-spacing: 2px; }
.hero-directory { position: relative; padding: 28px; border: 1px solid var(--glass-border); border-radius: 24px; background: var(--card); backdrop-filter: blur(18px); box-shadow: var(--shadow); transform: rotate(2deg); }
.hero-directory::before { content: ''; position: absolute; inset: 14px -12px -14px 12px; border-radius: inherit; border: 1px solid var(--border); z-index: -1; pointer-events: none; }
.directory-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 22px; font-size: 14px; font-weight: 600; }
.directory-label { color: var(--text-muted); font-size: 9px; font-weight: 400; letter-spacing: 1px; }
.directory-link { display: flex; align-items: center; gap: 18px; padding: 22px 0; border-top: 1px solid var(--border); transition: color .2s, transform .2s; }
.directory-link:hover { color: var(--accent); transform: translateX(4px); }
.directory-number { font-family: Georgia, serif; font-size: 28px; font-style: italic; color: var(--accent); opacity: .7; }
.directory-link > div { flex: 1; min-width: 0; }
.directory-link strong { display: block; margin-bottom: 7px; font-size: 17px; font-weight: 600; }
.directory-link div > span { display: block; color: var(--text-muted); font-size: 12px; line-height: 1.7; }
.directory-arrow { font-size: 21px; color: var(--text-muted); }
.directory-footer { display: flex; align-items: center; gap: 10px; border-top: 1px solid var(--border); padding-top: 20px; color: var(--text-muted); font-size: 11px; }
.directory-star { font-size: 23px; color: var(--accent); }
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
  display: inline-flex;
  align-items: center;
  gap: 22px;
  border: 0;
  border-radius: 12px;
  padding: 15px 22px;
  background: linear-gradient(var(--accent-soft), var(--accent-soft)), var(--card-solid);
  color: var(--accent);
  border: 1px solid var(--accent);
  font-size: 13px;
  cursor: pointer;
  transition: transform .2s, box-shadow .2s;
}
.hero-scroll:hover { transform: translateY(-2px); box-shadow: var(--shadow); }
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(8px); }
}

/* 主内容 */
.showcase {
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 20px 24px;
  scroll-margin-top: calc(var(--header-height) + 20px);
}
.showcase-inner {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  gap: 20px;
  align-items: start;
}
.showcase-carousel,
.showcase-player {
  min-width: 0;
}
.showcase-carousel { display: grid; gap: 20px; }
.showcase-cover { height: clamp(300px, 32vw, 400px); min-width: 0; overflow: hidden; border-radius: var(--radius); }
.showcase-discover { padding: 22px; border-radius: var(--radius); }
.showcase-discover header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 18px; color: var(--text-strong); font-size: 14px; }
.showcase-discover header small { color: var(--text-muted); font-size: 9px; letter-spacing: 2px; }
.discover-links { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.discover-links a { padding: 16px 12px; border: 1px solid var(--border); border-radius: 12px; background: var(--card-solid); transition: transform .2s, border-color .2s; }
.discover-links a:hover { transform: translateY(-3px); border-color: var(--accent); }
.discover-symbol { display: block; font-size: 24px; color: var(--accent); margin-bottom: 10px; }
.discover-links strong { display: flex; justify-content: space-between; gap: 6px; font-size: 13px; color: var(--text-strong); }.discover-links strong span { color: var(--accent); }
.discover-links p { margin: 10px 0 0; color: var(--text-muted); font-size: 11px; line-height: 1.8; }
@media(max-width:560px) { .discover-links { grid-template-columns: 1fr; }.discover-links a { display: grid; grid-template-columns: 30px 1fr; gap: 4px 12px; }.discover-symbol { grid-row: span 2; margin: 0; }.discover-links p { margin: 0; }.showcase-discover header small { display: none; }.daily-quote { padding: 16px 18px; } }
@media(prefers-reduced-motion:reduce) { .discover-links a { transition: none; } }
/* 最近评论：单容器下拉展开 */
.recent-comments {
  max-width: 1320px;
  margin: 0 auto 28px;
  padding: 0 20px;
}
.rc-container {
  border-radius: 18px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  overflow: hidden;
}
.rc-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 20px;
  border: none;
  background: transparent;
  color: var(--text-strong);
  cursor: pointer;
  text-align: left;
}
.rc-head-left {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.rc-title {
  position: relative;
  padding-left: 12px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-strong);
}
.rc-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(var(--accent), var(--accent-2));
}
.rc-badge {
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.rc-head-right {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
  font-size: 12px;
}
.rc-arrow {
  transition: transform 0.35s ease;
}
.rc-header.open .rc-arrow {
  transform: rotate(180deg);
}
.rc-drop {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
  transition: max-height 0.45s ease, opacity 0.35s ease;
  border-top: 1px solid transparent;
}
.rc-drop.open {
  max-height: 1200px;
  opacity: 1;
  border-top-color: var(--border);
}
.rc-item {
  display: flex;
  gap: 12px;
  padding: 14px 20px;
  cursor: pointer;
  border-bottom: 1px dashed var(--border);
  transition: background 0.25s ease;
}
.rc-item:last-child {
  border-bottom: none;
}
.rc-item:hover {
  background: var(--accent-soft);
}
.rc-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  flex-shrink: 0;
}
.rc-item-content {
  min-width: 0;
  flex: 1;
}
.rc-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 4px;
}
.rc-author {
  font-weight: 600;
  color: var(--text-strong);
  font-size: 13px;
}
.rc-time {
  color: var(--text-muted);
  font-size: 11px;
}
.rc-text {
  margin: 0;
  color: var(--text);
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}
.rc-page {
  margin-top: 4px;
  color: var(--accent);
  font-size: 12px;
}
.container {
  max-width: 1320px;
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
  min-width: 0;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 22px 20px;
  margin-bottom: 16px;
  background: var(--card);
  border: 1px solid var(--border);
}
.side-heading { display: flex; align-items: center; gap: 11px; }
.side-icon { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; width: 34px; height: 34px; border-radius: 11px; color: var(--accent-2); background: var(--accent-soft); }
.side-kicker { display: block; margin-bottom: 5px; color: var(--text-muted); font-size: 8px; letter-spacing: 1.6px; }
.side-heading h2 { margin: 0; font-size: 15px; font-weight: 650; color: var(--text-strong); }
.side-count { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 22px; padding: 0 5px; margin-left: auto; border: 1px solid var(--border); border-radius: 7px; color: var(--text-muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.side-description { color: var(--text-muted); font-size: 10px; line-height: 1.9; margin: 16px 0; }
.filter-placeholder { color: var(--text-muted); font-size: 11px; line-height: 1.9; padding: 4px 0; }
.filter-placeholder button { border: 0; padding: 5px; background: transparent; color: var(--accent); font-size: 11px; cursor: pointer; }
.cat-tree {
  background: transparent;
  max-height: 260px;
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
}
.cat-tree :deep(.el-tree-node__content) {
  background: transparent;
  color: var(--text);
  border-radius: 8px;
  height: 38px;
  margin-bottom: 3px;
  padding-right: 10px;
  font-size: 12px;
}
.category-label { min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; flex: 1; }
.cat-tree :deep(.el-tree-node__content:hover) {
  background: var(--accent-soft);
}
.cat-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--accent-soft);
  color: var(--accent);
}
.clear-filter {
  border: 0;
  padding: 12px 0 0;
  background: transparent;
  margin-top: 12px;
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  border-top: 1px solid var(--border);
  color: var(--accent);
  cursor: pointer;
}
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  max-height: 260px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  padding: 3px;
  margin: -3px;
}
.tag-chip {
  padding: 7px 10px;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 11px;
  line-height: 1.6;
  border-radius: 8px;
  color: var(--text);
  background: var(--glass-bg);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: background .2s, color .2s, border-color .2s;
}
.tag-hash { margin-right: 5px; color: var(--accent-2); opacity: .6; }
.tag-chip:hover {
  border-color: var(--accent);
}
.tag-chip.active {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--accent-soft);
}
.tag-chip.active .tag-hash { color: currentColor; opacity: 1; }
.filter-footer { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--border); }
.filter-footer button { border: 0; padding: 3px 0; background: transparent; color: var(--text-muted); font-size: 10px; cursor: pointer; }
.filter-footer .clear-tag { color: var(--accent); }
.tags-expand > span { margin-left: 6px; }
.content {
  flex: 1;
  min-width: 0;
}
.content-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}
.journal-context { display: flex; justify-content: space-between; gap: 12px; align-items: center; margin: -8px 0 24px; font-size: 12px; line-height: 1.8; color: var(--text-muted); }
.journal-context button { border: 0; padding: 6px 0 6px 12px; color: var(--accent); background: transparent; font-size: 11px; cursor: pointer; flex-shrink: 0; }
.section-heading > p { margin: 0 0 8px; color: var(--text-muted); font-size: 10px; letter-spacing: 2px; }
.section-heading > h2 { margin: 0; color: var(--text-strong); font-size: 26px; font-weight: 650; letter-spacing: 1px; }
.search-context { display: block; margin-top: 8px; font-size: 12px; color: var(--text-muted); overflow-wrap: anywhere; }
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
  .hero { min-height: auto; padding: 128px 24px 56px; }
  .hero-inner { gap: 32px; }
  .hero-directory { padding: 20px; transform: none; }
  .directory-label { display: none; }
  .directory-link { gap: 12px; padding: 18px 0; }
  .showcase-inner {
    grid-template-columns: 1fr;
  }
  .container {
    flex-direction: column;
  }
  .sidebar {
    width: 100%;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }
  .content { width: 100%; }
  .side-card {
    margin-bottom: 0;
  }
  .cat-tree, .tag-cloud { max-height: 180px; }
}
@media (max-width: 560px) {
  .journal-context { flex-wrap: wrap; margin-bottom: 20px; }
  .journal-context button { padding-left: 0; }
  .hero { padding: 116px 20px 44px; }
  .hero-inner { grid-template-columns: 1fr; gap: 32px; }
  .hero-title { font-size: clamp(42px, 12vw, 64px); }
  .hero-kicker { margin-bottom: 20px; font-size: 9px; letter-spacing: 2px; }
  .hero-description { font-size: 14px; }
  .hero-actions { gap: 18px; }
  .hero-note { display: none; }
  .hero-directory { border-radius: 18px; }
  .hero-directory::before { display: none; }
  .directory-heading { padding-bottom: 16px; }
  .directory-label { display: inline; }
  .directory-link { padding: 14px 0; }
  .directory-link strong { font-size: 15px; }
  .directory-footer { padding-top: 14px; }
  .content-head { align-items: flex-start; }
  .section-heading > h2 { font-size: 22px; }
  .switch button { padding: 8px 10px; font-size: 12px; }
  .switch button svg { display: none; }
  .sidebar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
  .side-card { padding: 18px 12px; }
  .side-heading { gap: 8px; flex-wrap: wrap; }
  .side-icon { width: 28px; height: 28px; border-radius: 9px; }
  .side-heading h2 { font-size: 13px; }
  .side-kicker { font-size: 7px; letter-spacing: .8px; }
  .side-count { display: none; }
  .side-description { font-size: 9px; margin: 12px 0; }
  .tag-chip { font-size: 10px; padding: 6px 8px; }
  .tag-hash { margin-right: 3px; }
  .cat-tree, .tag-cloud { max-height: 154px; }
}
</style>
