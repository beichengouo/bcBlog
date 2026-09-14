<template>
  <div class="music-manage">
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
    </el-card>

    <!-- 默认歌曲：歌单加载失败时前台播放 -->
    <el-card class="fallback-card">
      <template #header>
        <div class="toolbar">
          <span>默认歌曲（歌单加载失败时使用）</span>
          <el-button type="primary" plain @click="openFallbackEdit()">新增默认歌曲</el-button>
        </div>
      </template>
      <el-table :data="fallbackSongs" v-loading="fallbackLoading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="歌曲名称" min-width="140" />
        <el-table-column prop="artist" label="歌手" min-width="120" />
        <el-table-column prop="url" label="歌曲直链" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="openFallbackEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDeleteFallback(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增歌单 -->
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

    <!-- 新增/编辑默认歌曲 -->
    <el-dialog v-model="fallbackVisible" :title="fallbackForm.id ? '编辑默认歌曲' : '新增默认歌曲'" width="min(92vw, 480px)">
      <el-form :model="fallbackForm" label-width="80px">
        <el-form-item label="歌曲名称">
          <el-input v-model="fallbackForm.title" placeholder="如：起风了" maxlength="200" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-input v-model="fallbackForm.artist" placeholder="如：买辣椒也用券" maxlength="200" />
        </el-form-item>
        <el-form-item label="歌曲直链">
          <el-input v-model="fallbackForm.url" placeholder="https://...mp3" maxlength="500" />
        </el-form-item>
        <el-form-item label="封面图">
          <el-input v-model="fallbackForm.pic" placeholder="封面图地址，可留空" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fallbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="fallbackSaving" @click="onSaveFallback">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  playlistList,
  addPlaylist,
  deletePlaylist,
  setActivePlaylist,
  fallbackList,
  saveFallback,
  deleteFallback
} from '@/api/music'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const addVisible = ref(false)
const form = reactive({ name: '', playlistId: '' })

const fallbackSongs = ref([])
const fallbackLoading = ref(false)
const fallbackSaving = ref(false)
const fallbackVisible = ref(false)
const fallbackForm = reactive({ id: null, title: '', artist: '', url: '', pic: '' })

async function load() {
  loading.value = true
  try {
    list.value = await playlistList()
  } finally {
    loading.value = false
  }
}

async function loadFallback() {
  fallbackLoading.value = true
  try {
    fallbackSongs.value = await fallbackList()
  } finally {
    fallbackLoading.value = false
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

function openFallbackEdit(row) {
  fallbackForm.id = row ? row.id : null
  fallbackForm.title = row ? row.title : ''
  fallbackForm.artist = row ? row.artist || '' : ''
  fallbackForm.url = row ? row.url : ''
  fallbackForm.pic = row ? row.pic || '' : ''
  fallbackVisible.value = true
}

async function onSaveFallback() {
  if (!fallbackForm.title.trim()) {
    ElMessage.warning('请输入歌曲名称')
    return
  }
  if (!fallbackForm.url.trim()) {
    ElMessage.warning('请输入歌曲直链')
    return
  }
  fallbackSaving.value = true
  try {
    await saveFallback({ ...fallbackForm })
    ElMessage.success('保存成功')
    fallbackVisible.value = false
    loadFallback()
  } finally {
    fallbackSaving.value = false
  }
}

async function onDeleteFallback(row) {
  try {
    await ElMessageBox.confirm(`确定删除默认歌曲「${row.title}」吗？`, '删除默认歌曲', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteFallback(row.id)
  ElMessage.success('删除成功')
  loadFallback()
}

onMounted(() => {
  load()
  loadFallback()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.fallback-card {
  margin-top: 16px;
}
</style>
