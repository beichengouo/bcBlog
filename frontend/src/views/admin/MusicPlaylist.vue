<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>歌单管理</span>
        <el-button type="primary" @click="addVisible = true">新增歌单</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="歌单名称" min-width="140" />
      <el-table-column prop="playlistId" label="歌单 ID" width="140" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.active === 1 ? 'success' : 'info'">{{ row.active === 1 ? '使用中' : '未使用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="row.active !== 1" size="small" type="primary" @click="onActive(row)">启用</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addVisible" title="新增歌单" width="min(92vw, 420px)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="歌单名称">
          <el-input v-model="form.name" placeholder="如：我的歌单" maxlength="100" />
        </el-form-item>
        <el-form-item label="歌单 ID">
          <el-input v-model="form.playlistId" placeholder="网易云歌单地址里的数字 id" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAdd">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { playlistList, addPlaylist, deletePlaylist, setActivePlaylist } from '@/api/music'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const addVisible = ref(false)
const form = reactive({ name: '', playlistId: '' })

async function load() {
  loading.value = true
  try {
    list.value = await playlistList()
  } finally {
    loading.value = false
  }
}

async function onAdd() {
  if (!form.playlistId.trim()) {
    ElMessage.warning('请输入歌单 ID')
    return
  }
  saving.value = true
  try {
    await addPlaylist({ name: form.name, playlistId: form.playlistId.trim() })
    ElMessage.success('新增成功')
    addVisible.value = false
    form.name = ''
    form.playlistId = ''
    load()
  } finally {
    saving.value = false
  }
}

async function onActive(row) {
  await setActivePlaylist(row.id)
  ElMessage.success('已切换为该歌单')
  load()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除歌单「${row.name}」吗？`, '删除歌单', { type: 'warning' })
  } catch (e) {
    return
  }
  await deletePlaylist(row.id)
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
</style>
