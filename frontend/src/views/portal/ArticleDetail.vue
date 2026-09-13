<template>
  <div class="detail" v-loading="loading">
    <template v-if="article">
      <h1 class="title">{{ article.title }}</h1>
      <div class="meta">
        <span>{{ article.createTime }}</span>
        <span>浏览 {{ article.viewCount || 0 }}</span>
      </div>
      <!-- 正文为管理员使用富文本编辑器生成，属可信内容，因此直接渲染 HTML -->
      <div class="content" v-html="article.content"></div>
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
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPortalArticle } from '@/api/article'
import { listComments, saveComment } from '@/api/comment'

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

async function load() {
  loading.value = true
  try {
    const data = await getPortalArticle(route.params.id)
    article.value = data.article
    prev.value = data.prev
    next.value = data.next
    window.scrollTo(0, 0)
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

function go(id) {
  router.push(`/portal/article/${id}`)
}

// 上一篇/下一篇切换时，同一组件复用，需要监听 id 变化重新加载
watch(() => route.params.id, load)
onMounted(load)
</script>

<style scoped>
.detail {
  max-width: 820px;
  margin: 0 auto;
  background: #fff;
  border-radius: 8px;
  padding: 28px;
}
.title {
  font-size: 26px;
  margin: 0 0 12px;
  line-height: 1.4;
}
.meta {
  display: flex;
  gap: 16px;
  color: #999;
  font-size: 13px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 20px;
}
.content {
  line-height: 1.8;
  color: #333;
  word-break: break-word;
}
.content :deep(img) {
  max-width: 100%;
  height: auto;
}
.nearby {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
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
  color: #999;
  font-size: 12px;
  margin-bottom: 4px;
}
.nearby-item .link {
  color: #409eff;
}
.nearby-item.right {
  text-align: right;
}
.nearby-item.disabled {
  color: #c0c4cc;
  cursor: default;
}
.comments {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}
.comments-title {
  font-size: 18px;
  margin: 0 0 16px;
}
.comment-form {
  margin-bottom: 24px;
}
.comment-textarea {
  margin: 12px 0;
}
.comment-item {
  padding: 14px 0;
  border-bottom: 1px solid #f5f5f5;
}
.comment-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
.comment-nickname {
  font-weight: 600;
  color: #333;
}
.comment-time {
  color: #999;
  font-size: 12px;
}
.comment-content {
  color: #555;
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
  }
  .title {
    font-size: 22px;
  }
}
</style>
