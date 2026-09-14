<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>邀请码管理</span>
        <div class="head-right">
          <span class="muted">邀请码可无限次使用，短时间内被频繁使用会自动更换</span>
          <el-button type="primary" plain @click="onGenerateMine">生成我的邀请码</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="邀请码" width="160" />
      <el-table-column prop="creatorName" label="所属用户" min-width="140" />
      <el-table-column prop="useCount" label="使用次数" width="110" />
      <el-table-column prop="lastUsedAt" label="最后使用时间" width="180">
        <template #default="{ row }">{{ row.lastUsedAt || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="copy(row.code)">复制</el-button>
          <el-button size="small" type="primary" plain @click="onRegenerate(row)">重新生成</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inviteList, regenerateInvite, generateMyInvite } from '@/api/member'
import { copyText } from '@/utils/content'

const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await inviteList()
  } finally {
    loading.value = false
  }
}

async function copy(code) {
  try {
    await copyText(code)
    ElMessage.success('邀请码已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

async function onRegenerate(row) {
  try {
    await ElMessageBox.confirm(`确定重新生成「${row.creatorName}」的邀请码吗？旧邀请码会失效。`, '重新生成', { type: 'warning' })
  } catch (e) {
    return
  }
  await regenerateInvite(row.creatorId)
  ElMessage.success('已重新生成')
  load()
}

async function onGenerateMine() {
  const code = await generateMyInvite()
  if (code) {
    ElMessage.success(`我的邀请码：${code.code}`)
    load()
  }
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
.muted {
  color: #909399;
  font-size: 12px;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
