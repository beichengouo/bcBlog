<template>
  <div class="native-comments">
    <h2 class="comments-title">评论（{{ total }}）</h2>

    <div v-if="memberStore.isLogin" class="comment-form">
      <el-input
        v-model="content"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        placeholder="写下你的评论..."
      />
      <div class="form-actions">
        <EmojiPicker @select="onEmojiSelect" />
        <el-button type="primary" :loading="saving" @click="onSubmit">发表评论</el-button>
      </div>
    </div>
    <div v-else class="login-tip">
      <span>登录后才能发表评论</span>
      <el-button type="primary" @click="router.push('/portal/login')">去登录</el-button>
    </div>

    <div v-if="comments.length" class="comment-list">
      <div v-for="c in comments" :key="c.id" class="comment-item">
        <img v-if="c.avatar" class="comment-avatar" :src="c.avatar" alt="" />
        <div v-else class="comment-avatar placeholder">{{ (c.nickname || 'U').slice(0, 1) }}</div>
        <div class="comment-body">
          <div class="comment-head">
            <span class="comment-nickname">{{ c.nickname }}</span>
            <span v-if="c.level" class="level-badge">Lv.{{ c.level }} {{ c.levelName }}</span>
            <span class="comment-time">{{ c.createTime }}</span>
          </div>
          <div class="comment-content" v-html="renderEmojiContent(c.content)"></div>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="还没有评论" :image-size="80" />

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
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listComments, saveComment } from '@/api/comment'
import { useMemberStore } from '@/store/member'
import EmojiPicker from '@/components/portal/EmojiPicker.vue'
import { emojiToken, renderEmojiContent } from '@/utils/content'

const props = defineProps({
  articleId: { type: [Number, String], required: true }
})

const router = useRouter()
const memberStore = useMemberStore()

const comments = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const saving = ref(false)
const content = ref('')

async function load() {
  loading.value = true
  try {
    const data = await listComments({ articleId: props.articleId, page: page.value, size: size.value })
    comments.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (!content.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  saving.value = true
  try {
    await saveComment({ articleId: Number(props.articleId), content: content.value.trim() })
    ElMessage.success('评论成功')
    content.value = ''
    page.value = 1
    await load()
    // 评论可能增加经验，刷新用户信息
    memberStore.fetchInfo(true).catch(() => {})
  } finally {
    saving.value = false
  }
}

function onEmojiSelect(emoji) {
  content.value += emojiToken(emoji.url)
}

watch(() => props.articleId, () => {
  page.value = 1
  load()
})
onMounted(load)
</script>

<style scoped>
.native-comments {
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
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 22px;
}
.comment-form .el-button {
  align-self: auto;
}
.form-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
.login-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 22px;
  border-radius: 12px;
  background: var(--accent-soft);
  color: var(--text-muted);
  font-size: 14px;
}
.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}
.comment-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.comment-avatar.placeholder {
  color: #fff;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.comment-body {
  min-width: 0;
  flex: 1;
}
.comment-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.comment-nickname {
  font-weight: 600;
  color: var(--text-strong);
}
.level-badge {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.comment-time {
  color: var(--text-muted);
  font-size: 12px;
  margin-left: auto;
}
.comment-content {
  color: var(--text);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.comment-content :deep(.comment-emoji) {
  width: 22px;
  height: 22px;
  vertical-align: -4px;
  margin: 0 1px;
  object-fit: contain;
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
</style>
