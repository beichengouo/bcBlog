<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>评论管理</span>
        <el-radio-group v-model="status" @change="reload">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="1">已通过</el-radio-button>
          <el-radio-button :value="0">待审核</el-radio-button>
          <el-radio-button :value="2">已拒绝</el-radio-button>
        </el-radio-group>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="articleTitle" label="文章" min-width="160" show-overflow-tooltip />
      <el-table-column prop="nickname" label="昵称" width="120" show-overflow-tooltip />
      <el-table-column prop="email" label="邮箱" width="170" show-overflow-tooltip />
      <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 1" link type="success" @click="onStatus(row, 1)">通过</el-button>
          <el-button v-if="row.status !== 2" link type="warning" @click="onStatus(row, 2)">拒绝</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminCommentPage, updateCommentStatus, deleteComment } from '@/api/comment'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref(null)
const loading = ref(false)

function statusType(s) {
  return s === 1 ? 'success' : s === 2 ? 'danger' : 'info'
}

function statusText(s) {
  return s === 1 ? '已通过' : s === 2 ? '已拒绝' : '待审核'
}

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (status.value !== null && status.value !== undefined && status.value !== '') {
      params.status = status.value
    }
    const data = await adminCommentPage(params)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

async function onStatus(row, s) {
  await updateCommentStatus({ id: row.id, status: s })
  ElMessage.success('操作成功')
  load()
}

async function onDelete(row) {
  await ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
  await deleteComment(row.id)
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
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
