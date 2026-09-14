<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>表情包管理</span>
        <div class="upload-row">
          <el-input v-model="pack" placeholder="表情包名称" maxlength="50" class="pack-input" />
          <el-upload
            :action="'/api/admin/upload/image'"
            :headers="uploadHeaders"
            :show-file-list="false"
            accept="image/*"
            :on-success="onUploadSuccess"
            :on-error="onUploadError"
          >
            <el-button type="primary">上传表情</el-button>
          </el-upload>
        </div>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="pack" label="表情包" width="140" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column label="预览" width="100">
        <template #default="{ row }">
          <img class="emoji-preview" :src="row.url" alt="" />
        </template>
      </el-table-column>
      <el-table-column prop="url" label="地址" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="onToggle(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { emojiList, saveEmoji, deleteEmoji } from '@/api/emoji'

const list = ref([])
const loading = ref(false)
const pack = ref('默认表情')
const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }

async function load() {
  loading.value = true
  try {
    list.value = await emojiList()
  } finally {
    loading.value = false
  }
}

async function onUploadSuccess(res) {
  if (res && res.code === 200 && res.data) {
    await saveEmoji({ pack: pack.value || '默认表情', name: '', url: res.data, enabled: 1, sortOrder: 0 })
    ElMessage.success('表情已上传')
    load()
  } else {
    ElMessage.error((res && res.msg) || '表情上传失败')
  }
}

function onUploadError() {
  ElMessage.error('表情上传失败')
}

async function onToggle(row) {
  await saveEmoji({ ...row, enabled: row.enabled === 1 ? 0 : 1 })
  ElMessage.success('已更新')
  load()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除这个表情吗？', '删除表情', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteEmoji(row.id)
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
.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pack-input {
  width: 180px;
}
.emoji-preview {
  width: 40px;
  height: 40px;
  object-fit: contain;
}
</style>
