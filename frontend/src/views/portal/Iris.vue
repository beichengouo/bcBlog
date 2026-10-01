<template>
  <div class="iris-page">
    <!-- 档案 -->
    <section class="iris-hero card glass">
      <div class="hero-avatar">
        <img v-if="profile.avatar" :src="profile.avatar" alt="IRIS" />
        <div v-else class="avatar-placeholder">IRIS</div>
      </div>
      <div class="hero-main">
        <div class="hero-name">
          <h1>{{ profile.displayName || '伊莉丝 IRIS' }}</h1>
          <span class="ai-badge">AI 生成</span>
        </div>
        <div class="hero-meta">
          <span v-if="profile.modelNo">型号 {{ profile.modelNo }}</span>
          <span v-if="statusLine" class="hero-status">当前状态：{{ statusLine }}</span>
        </div>
        <p v-if="profile.tagline" class="hero-tagline">“{{ profile.tagline }}”</p>
        <p v-if="profile.bio" class="hero-bio">{{ profile.bio }}</p>
        <div class="hero-note">
          本页与她的文章、评论均由站内 AI 依据站长设定自动生成，已在标题与评论处标注。
          <router-link to="/portal/terms">用户协议</router-link> ·
          <router-link to="/portal/privacy">隐私政策</router-link>
        </div>
      </div>
    </section>

    <!-- 她写的文章 -->
    <section class="iris-articles">
      <h2 class="section-title">她写的文章</h2>
      <div v-if="articles.length" class="article-grid">
        <router-link
          v-for="a in articles"
          :key="a.id"
          class="card article-card"
          :to="'/portal/article/' + a.id"
        >
          <div class="cover">
            <img v-if="a.cover" :src="a.cover" alt="" loading="lazy" />
            <div v-else class="cover-fallback">IRIS</div>
          </div>
          <div class="body">
            <h3>{{ a.title }}</h3>
            <p v-if="a.summary">{{ a.summary }}</p>
            <div class="meta">
              <span>{{ shortDate(a.createTime) }}</span>
              <span>浏览 {{ a.viewCount || 0 }}</span>
            </div>
          </div>
        </router-link>
      </div>
      <el-empty v-else description="她还没有写过文章" :image-size="80" />
    </section>

    <!-- 最近活动 -->
    <section class="iris-timeline">
      <h2 class="section-title">最近活动</h2>
      <div class="timeline">
        <div v-for="item in activities" :key="item.id" class="tl-item">
          <span class="tl-time">{{ shortTime(item.createTime) }}</span>
          <span class="tl-type">{{ typeText(item.activityType) }}</span>
          <span class="tl-text">
            <template v-if="item.articleId">
              <router-link class="tl-link" :to="'/portal/article/' + item.articleId">
                <template v-if="item.title">《{{ item.title }}》</template>
              </router-link>
              <span class="tl-excerpt">{{ item.content }}</span>
            </template>
            <template v-else>
              <template v-if="item.title">（{{ item.title }}）</template>{{ item.content }}
            </template>
          </span>
        </div>
        <el-empty v-if="!activities.length" description="暂无活动" :image-size="70" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { irisPortal } from '@/api/siteAi'

const profile = ref({})
const statusLine = ref('')
const activities = ref([])
const articles = ref([])

function shortDate(text) {
  return text ? String(text).slice(0, 10) : ''
}

function shortTime(text) {
  return text ? String(text).slice(5, 16) : ''
}

function typeText(type) {
  if (type === 'article') return '写文章'
  if (type === 'comment') return '评论'
  if (type === 'reply') return '回复'
  if (type === 'status') return '状态'
  if (type === 'report') return '运行情况'
  return type
}

onMounted(async () => {
  try {
    const data = await irisPortal()
    profile.value = data.profile || {}
    statusLine.value = data.status || ''
    activities.value = data.activities || []
    articles.value = data.articles || []
  } catch (e) {
    // 接口异常时保持空页面
  }
})
</script>

<style scoped>.tl-link {
  color: var(--accent);
  text-decoration: none;
  margin-right: 6px;
}
.tl-link:hover {
  text-decoration: underline;
}
.tl-excerpt {
  color: var(--text-muted);
}
.iris-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: calc(var(--header-height) + 28px) 20px 48px;
}
.iris-hero {
  display: flex;
  gap: 20px;
  padding: 22px 24px;
  border-radius: 18px;
  margin-bottom: 26px;
}
.hero-avatar img,
.avatar-placeholder {
  width: 108px;
  height: 108px;
  border-radius: 16px;
  object-fit: cover;
}
.avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--accent);
  background: var(--accent-soft);
}
.hero-main {
  flex: 1;
  min-width: 0;
}
.hero-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hero-name h1 {
  margin: 0;
  font-size: 22px;
}
.ai-badge {
  padding: 2px 8px;
  font-size: 12px;
  color: #fff;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-muted);
}
.hero-status {
  color: var(--accent);
}
.hero-tagline {
  margin: 10px 0 0;
  color: var(--text);
}
.hero-bio {
  margin: 10px 0 0;
  line-height: 1.8;
  color: var(--text-muted);
  white-space: pre-wrap;
}
.hero-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-muted);
}
.hero-note a {
  color: var(--accent);
}
.section-title {
  margin: 0 0 12px;
  font-size: 17px;
}
.article-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.article-card {
  display: block;
  border-radius: 14px;
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.article-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-hover);
}
.cover {
  aspect-ratio: 16 / 9;
  background: var(--accent-soft);
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  font-weight: 700;
  letter-spacing: 2px;
}
.article-card .body {
  padding: 14px;
}
.article-card h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
.article-card p {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.article-card .meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
}
.iris-timeline {
  margin-top: 30px;
}
.timeline {
  border-radius: 14px;
  border: 1px solid var(--border);
  background: var(--card);
  padding: 6px 16px;
}
.tl-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 13px;
  line-height: 1.7;
}
.tl-item:last-child {
  border-bottom: none;
}
.tl-time {
  flex-shrink: 0;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.tl-type {
  flex-shrink: 0;
  color: var(--accent);
}
.tl-text {
  flex: 1;
  min-width: 0;
  color: var(--text);
  word-break: break-word;
}
@media (max-width: 640px) {
  .iris-hero {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  .hero-meta {
    justify-content: center;
  }
}
</style>
