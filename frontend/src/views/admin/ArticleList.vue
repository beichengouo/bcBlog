<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <div class="toolbar-left">
          <span>文章列表</span>
          <el-button type="primary" @click="goCreate">新增文章</el-button>
        </div>
        <div class="toolbar-right">
          <el-input v-model="keyword" placeholder="按标题搜索" clearable class="search" @keyup.enter="load" />
          <el-button type="primary" @click="load">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" min-width="200" />
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
      class="pager"
      @current-change="load"
    />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
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

async function load() {
  loading.value = true
  try {
    const data = await listAdminArticles({ page: page.value, size: size.value, keyword: keyword.value })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function goCreate() {
  router.push('/admin/articles/edit')
}

function goEdit(row) {
  router.push(`/admin/articles/edit/${row.id}`)
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除文章「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteArticle(row.id)
  ElMessage.success('删除成功')
  load()
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
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.search {
  width: 220px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
