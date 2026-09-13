<template>
  <div class="detail" v-loading="loading">
    <template v-if="article">
      <h1 class="title">{{ article.title }}</h1>
      <div class="meta">
        <span>{{ article.authorName || '管理员' }}</span>
        <span>{{ article.createTime }}</span>
        <span>浏览 {{ article.viewCount || 0 }}</span>
      </div>
      <!-- 正文为管理员使用富文本编辑器生成，属可信内容，因此直接渲染 HTML -->
      <div ref="contentEl" class="content rich-text" v-html="article.content" @click="onContentClick"></div>
      <div class="nearby">
        <div v-if="prev" class="nearby-item" @click="go(prev.id)">
          <span class="label">上一篇</span>
          <span class="link">{{ prev.title }}</span>
        </div>
        <div v-else class="nearby-item disabled">上一篇：无</div>
        <div v-if="next" class="nearby-item right" @click="go(next.id)">
          <span class="label">下一篇</span>
          <span class="link">{{ next.title }}</span>
        </div>
        <div v-else class="nearby-item right disabled">下一篇：无</div>
      </div>

      <div class="comments">
        <h2 class="comments-title">评论（{{ commentTotal }}）</h2>
        <el-form :model="commentForm" class="comment-form">
          <el-row :gutter="12">
            <el-col :xs="24" :sm="12">
              <el-input v-model="commentForm.nickname" placeholder="昵称（必填）" maxlength="50" />
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-input v-model="commentForm.email" placeholder="邮箱（选填）" maxlength="100" />
            </el-col>
          </el-row>
          <el-input
            v-model="commentForm.content"
            type="textarea"
            :rows="3"
            maxlength="1000"
            placeholder="写下你的评论..."
            class="comment-textarea"
          />
          <el-button type="primary" :loading="commentSaving" @click="onSubmitComment">发表评论</el-button>
        </el-form>

        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-head">
            <span class="comment-nickname">{{ c.nickname }}</span>
            <span class="comment-time">{{ c.createTime }}</span>
          </div>
          <div class="comment-content">{{ c.content }}</div>
        </div>
        <el-empty v-if="!commentLoading && !comments.length" description="还没有评论" :image-size="80" />
        <el-pagination
          v-if="commentTotal > commentSize"
          v-model:current-page="commentPage"
          :page-size="commentSize"
          :total="commentTotal"
          layout="prev, pager, next"
          class="comment-pager"
          @current-change="loadComments"
        />
      </div>
    </template>
    <el-empty v-if="!loading && !article" description="文章不存在" />

    <!-- 图片灯箱 -->
    <teleport to="body">
      <transition name="lightbox">
        <div v-if="lightbox.src" class="lightbox" @click="lightbox.src = ''">
          <button class="lightbox-btn prev" @click.stop="lightboxStep(-1)" aria-label="上一张">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6" /></svg>
          </button>
          <img :src="lightbox.src" :alt="lightbox.alt || ''" />
          <button class="lightbox-btn next" @click.stop="lightboxStep(1)" aria-label="下一张">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6" /></svg>
          </button>
          <button class="lightbox-btn close" @click.stop="lightbox.src = ''" aria-label="关闭">×</button>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup>
import { ref, reactive, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPortalArticle } from '@/api/article'
import { listComments, saveComment } from '@/api/comment'
import { decorateContent } from '@/utils/content'

const route = useRoute()
const router = useRouter()

const article = ref(null)
const prev = ref(null)
const next = ref(null)
const loading = ref(false)

const comments = ref([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentSize = ref(10)
const commentLoading = ref(false)
const commentSaving = ref(false)
const commentForm = reactive({ nickname: '', email: '', content: '' })
const contentEl = ref()
const lightbox = ref({ src: '', alt: '', images: [] })

async function load() {
  loading.value = true
  try {
    const data = await getPortalArticle(route.params.id)
    article.value = data.article
    prev.value = data.prev
    next.value = data.next
    window.scrollTo(0, 0)
    await nextTick()
    decorateContent(contentEl.value)
  } finally {
    loading.value = false
  }
  commentPage.value = 1
  loadComments()
}

async function loadComments() {
  commentLoading.value = true
  try {
    const data = await listComments({ articleId: route.params.id, page: commentPage.value, size: commentSize.value })
    comments.value = data.list
    commentTotal.value = data.total
  } finally {
    commentLoading.value = false
  }
}

async function onSubmitComment() {
  if (!commentForm.nickname.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }
  if (!commentForm.content.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  commentSaving.value = true
  try {
    await saveComment({
      articleId: Number(route.params.id),
      nickname: commentForm.nickname,
      email: commentForm.email,
      content: commentForm.content
    })
    ElMessage.success('评论成功')
    commentForm.content = ''
    commentPage.value = 1
    loadComments()
  } finally {
    commentSaving.value = false
  }
}

/** 点击正文里的图片时打开灯箱。 */
function onContentClick(e) {
  const img = e.target.closest('img')
  if (!img || !contentEl.value) return
  const images = Array.from(contentEl.value.querySelectorAll('img'))
  lightbox.value = {
    src: img.src,
    alt: img.alt || '',
    images: images.map((i) => ({ src: i.src, alt: i.alt || '' }))
  }
}

/** 上一张 / 下一张切换。 */
function lightboxStep(delta) {
  const { src, images } = lightbox.value
  if (!images.length) return
  const idx = images.findIndex((i) => i.src === src)
  const next = (idx + delta + images.length) % images.length
  lightbox.value.src = images[next].src
  lightbox.value.alt = images[next].alt
}

/** 键盘方向键切换图片，Esc 关闭灯箱。 */
function onKeydown(e) {
  if (!lightbox.value.src) return
  if (e.key === 'Escape') {
    lightbox.value.src = ''
  } else if (e.key === 'ArrowLeft') {
    lightboxStep(-1)
  } else if (e.key === 'ArrowRight') {
    lightboxStep(1)
  }
}

function go(id) {
  router.push(`/portal/article/${id}`)
}

// 上一篇/下一篇切换时，同一组件复用，需要监听 id 变化重新加载
watch(() => route.params.id, load)
onMounted(load)
onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.detail {
  max-width: 820px;
  margin: 0 auto;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  backdrop-filter: blur(12px);
  padding: calc(var(--header-height) + 20px) 28px 28px;
  box-shadow: var(--shadow);
}
.title {
  font-size: 26px;
  margin: 0 0 12px;
  line-height: 1.4;
  color: var(--text-strong);
}
.meta {
  display: flex;
  gap: 16px;
  color: var(--text-muted);
  font-size: 13px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}
.content {
  word-break: break-word;
}
.content :deep(img) {
  cursor: zoom-in;
}
.nearby {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.nearby-item {
  max-width: 45%;
  cursor: pointer;
}
.nearby-item .label {
  display: block;
  color: var(--text-muted);
  font-size: 12px;
  margin-bottom: 4px;
}
.nearby-item .link {
  color: var(--accent);
}
.nearby-item.right {
  text-align: right;
}
.nearby-item.disabled {
  color: var(--text-muted);
  cursor: default;
}
.comments {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.comments-title {
  font-size: 18px;
  margin: 0 0 16px;
  color: var(--text-strong);
}
.comment-form {
  margin-bottom: 24px;
}
.comment-textarea {
  margin: 12px 0;
}
.comment-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}
.comment-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
.comment-nickname {
  font-weight: 600;
  color: var(--text-strong);
}
.comment-time {
  color: var(--text-muted);
  font-size: 12px;
}
.comment-content {
  color: var(--text);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.comment-pager {
  margin-top: 16px;
  justify-content: center;
}
@media (max-width: 768px) {
  .detail {
    padding: 16px;
    padding-top: calc(var(--header-height) + 16px);
  }
  .title {
    font-size: 22px;
  }
}

/* 图片灯箱 */
.lightbox {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.82);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}
.lightbox img {
  max-width: 92vw;
  max-height: 88vh;
  border-radius: 12px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5);
}
.lightbox-btn {
  position: absolute;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  cursor: pointer;
  border-radius: 50%;
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease, transform 0.2s ease;
}
.lightbox-btn:hover {
  background: rgba(255, 255, 255, 0.24);
  transform: scale(1.05);
}
.lightbox-btn.prev {
  left: 20px;
  top: 50%;
  transform: translateY(-50%);
}
.lightbox-btn.next {
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
}
.lightbox-btn.close {
  top: 20px;
  right: 20px;
  font-size: 26px;
}
.lightbox-enter-active,
.lightbox-leave-active {
  transition: opacity 0.22s ease;
}
.lightbox-enter-from,
.lightbox-leave-to {
  opacity: 0;
}
</style>
