<template>
  <el-card class="content-library">
    <template #header>
      <div class="toolbar">
        <div class="toolbar-left">
          <div class="library-title"><strong>全部文章</strong><span>{{ loading ? '正在整理列表…' : `当前结果 ${total} 篇` }}</span></div>
          <el-button type="primary" @click="goCreate">新增文章</el-button>
        </div>
        <div class="toolbar-right">
          <el-input v-model="keyword" placeholder="搜索文章标题…" aria-label="搜索文章标题" clearable class="search" @keyup.enter="onSearch" @clear="onSearch" />
          <el-button @click="onSearch">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="文章" min-width="310">
        <template #default="{ row }">
          <div class="article-cell">
            <img v-if="row.cover && !failedCovers.has(row.cover)" class="article-thumb" :src="row.cover" alt="" loading="lazy" @error="failedCovers.add(row.cover)" />
            <span v-else class="article-thumb thumb-fallback" aria-hidden="true">{{ (row.title || '文').slice(0, 1) }}</span>
            <div class="article-cell-copy"><router-link :to="`/admin/articles/edit/${row.id}`">{{ row.title }}</router-link><span>{{ row.authorName || '管理员' }}<template v-if="row.aiGenerated === 1"> · AI 创作</template></span></div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="置顶" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isTop === 1" type="warning">置顶</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已发布' : '草稿' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      :pager-count="5"
      class="pager"
      @current-change="load"
    />
  </el-card>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAdminArticles, deleteArticle } from '@/api/article'

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const failedCovers = ref(new Set())
let requestId = 0

function onSearch() {
  page.value = 1
  load()
}

async function load() {
  const id = ++requestId
  loading.value = true
  try {
    const data = await listAdminArticles({ page: page.value, size: size.value, keyword: keyword.value })
    if (id !== requestId) return
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    // 请求拦截器展示错误；保留已显示的列表，允许重新查询。
  } finally {
    if (id === requestId) loading.value = false
  }
}

function goCreate() {
  router.push('/admin/articles/edit')
}

function goEdit(row) {
  router.push(`/admin/articles/edit/${row.id}`)
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除文章「${row.title}」吗？`, '提示', { type: 'warning' })
    await deleteArticle(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && page.value > 1) page.value--
    await load()
  } catch (e) {
    // 取消确认时保持列表不变；接口错误由请求拦截器展示。
  }
}

onMounted(load)
onUnmounted(() => { requestId++ })
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.search {
  width: 240px;
}
.library-title { margin-right: 16px; }
.library-title strong { display: block; color: var(--admin-ink); font-size: 15px; font-weight: 600; }
.library-title > span { display: block; color: var(--admin-muted); font-size: 11px; margin-top: 5px; font-weight: 400; }
.article-cell { display: flex; align-items: center; gap: 14px; padding: 3px 0; }
.article-thumb { width: 72px; height: 48px; object-fit: cover; border-radius: 8px; flex-shrink: 0; }
.thumb-fallback { display: inline-flex; align-items: center; justify-content: center; background: #f0eaf8; color: #9780b9; font-family: SimSun, serif; font-size: 25px; }
.article-cell-copy { min-width: 0; }
.article-cell-copy > a { display: block; color: var(--admin-ink); font-size: 13px; font-weight: 550; line-height: 1.7; overflow-wrap: anywhere; }
.article-cell-copy > a:hover { color: var(--admin-primary); }
.article-cell-copy > span { display: block; margin-top: 5px; color: var(--admin-muted); font-size: 11px; }
.pager {
  margin-top: 24px;
  justify-content: flex-end;
}
@media (max-width: 600px) {
  .toolbar { gap: 18px; }
  .toolbar-left, .toolbar-right { width: 100%; }
  .toolbar-left { justify-content: space-between; }
  .toolbar-right .search { width: auto; flex: 1; min-width: 100px; }
  .library-title { margin-right: 0; }
}
</style>
