<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>Gitalk 评论（GitHub Issues）</span>
        <div class="filter">
          <el-input v-model="keyword" placeholder="搜索内容 / 用户 / 文章" clearable class="search" @keyup.enter="reload" />
          <el-input v-model="issueNumber" placeholder="文章 Issue 编号（可选）" clearable class="issue" @keyup.enter="reload" />
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column label="用户" width="160">
        <template #default="{ row }">
          <div class="user">
            <img class="avatar" :src="row.avatar" alt="" />
            <span>{{ row.author }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="评论内容" min-width="260">
        <template #default="{ row }">
          <div class="comment-text">{{ row.body }}</div>
          <a v-if="row.htmlUrl" class="gh-link" :href="row.htmlUrl" target="_blank" rel="noopener noreferrer">在 GitHub 查看</a>
        </template>
      </el-table-column>
      <el-table-column label="所属文章" min-width="180">
        <template #default="{ row }">
          <span v-if="row.pageTitle">{{ row.pageTitle }}</span>
          <span v-else class="muted">—</span>
          <div v-if="row.pagePath" class="muted">{{ row.pagePath }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="openReply(row)">回复</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      class="pager"
      @current-change="load"
    />

    <el-dialog v-model="replyVisible" title="回复评论" width="min(92vw, 520px)">
      <div class="reply-target">
        <span class="muted">回复文章：</span>
        <strong>{{ replyForm.pageTitle || ('Issue #' + replyForm.issueNumber) }}</strong>
      </div>
      <el-input
        v-model="replyForm.body"
        type="textarea"
        :rows="5"
        maxlength="1000"
        show-word-limit
        placeholder="输入回复内容，支持 Markdown"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="onReply">回复</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminGitalkComments, deleteGitalkComment, replyGitalkComment } from '@/api/gitalk'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const issueNumber = ref('')
const loading = ref(false)

const replyVisible = ref(false)
const replying = ref(false)
const replyForm = reactive({ issueNumber: null, pageTitle: '', body: '' })

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (issueNumber.value.trim()) params.issueNumber = issueNumber.value.trim()
    const data = await adminGitalkComments(params)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function onReset() {
  keyword.value = ''
  issueNumber.value = ''
  reload()
}

function openReply(row) {
  replyForm.issueNumber = row.issueNumber
  replyForm.pageTitle = row.pageTitle
  replyForm.body = ''
  replyVisible.value = true
}

async function onReply() {
  if (!replyForm.body.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replying.value = true
  try {
    await replyGitalkComment({ issueNumber: replyForm.issueNumber, body: replyForm.body.trim() })
    ElMessage.success('回复成功')
    replyVisible.value = false
    load()
  } finally {
    replying.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm('删除后 GitHub 上的这条评论也会消失，确定删除吗？', '删除评论', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteGitalkComment(row.id)
  ElMessage.success('删除成功')
  load()
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.search {
  width: 220px;
}
.issue {
  width: 170px;
}
.user {
  display: flex;
  align-items: center;
  gap: 8px;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
}
.comment-text {
  color: #303133;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.gh-link {
  display: inline-block;
  margin-top: 4px;
  color: #409eff;
  font-size: 12px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.reply-target {
  margin-bottom: 10px;
  font-size: 13px;
}
</style>
