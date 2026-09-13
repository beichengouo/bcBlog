<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>背景壁纸管理</span>
        <div class="head-ops">
          <el-button @click="onClear('portal')">前台恢复默认</el-button>
          <el-button @click="onClear('admin')">后台恢复默认</el-button>
          <el-upload
            :action="'/api/admin/background/upload'"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="onUploadSuccess"
            :on-error="onUploadError"
          >
            <el-button type="primary">上传背景</el-button>
          </el-upload>
        </div>
      </div>
    </template>

    <p class="tip">壁纸库前台和后台共用；可分别点“设为前台 / 设为后台”独立启用，也可恢复默认（主题渐变）。</p>

    <div class="opacity-row">
      <span class="opacity-label">后台背景透明度</span>
      <el-slider v-model="opacity" :min="0.1" :max="1" :step="0.05" class="opacity-slider" />
      <el-button type="primary" plain :loading="opacitySaving" @click="onSaveOpacity">保存</el-button>
    </div>

    <div class="bg-grid">
      <div v-for="b in list" :key="b.id" class="bg-card">
        <div class="preview">
          <img v-if="b.type === 'image'" :src="b.url" alt="" />
          <video v-else :src="b.url" muted preload="metadata"></video>
          <div class="badges">
            <span v-if="b.portalActive === 1" class="tag portal">前台使用中</span>
            <span v-if="b.adminActive === 1" class="tag admin">后台使用中</span>
          </div>
        </div>
        <div class="info">
          <div class="name" :title="b.name">{{ b.name }}</div>
          <div class="ops">
            <el-button size="small" type="primary" plain @click="onActive(b, 'portal')">设为前台</el-button>
            <el-button size="small" type="warning" plain @click="onActive(b, 'admin')">设为后台</el-button>
            <el-button size="small" type="danger" @click="onDelete(b)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="!list.length" description="暂无背景，请上传" :image-size="80" class="empty" />
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { backgroundList, deleteBackground, setActiveBackground, clearActiveBackground } from '@/api/background'
import { getAdminBgOpacity, saveAdminBgOpacity } from '@/api/config'

const list = ref([])
const opacity = ref(1)
const opacitySaving = ref(false)
const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }

async function load() {
  list.value = await backgroundList()
}

function onUploadSuccess(res) {
  if (res && res.code === 200) {
    ElMessage.success('上传成功')
    load()
  } else {
    ElMessage.error((res && res.msg) || '上传失败')
  }
}

function onUploadError() {
  ElMessage.error('上传失败')
}

async function onActive(row, scope) {
  await setActiveBackground(row.id, scope)
  ElMessage.success(scope === 'portal' ? '已设为前台背景' : '已设为后台背景')
  load()
}

async function onClear(scope) {
  await clearActiveBackground(scope)
  ElMessage.success(scope === 'portal' ? '前台已恢复默认背景' : '后台已恢复默认背景')
  load()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除背景「${row.name}」吗？`, '删除背景', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteBackground(row.id)
  ElMessage.success('删除成功')
  load()
}

async function onSaveOpacity() {
  opacitySaving.value = true
  try {
    await saveAdminBgOpacity(opacity.value)
    ElMessage.success('后台背景透明度已保存')
  } finally {
    opacitySaving.value = false
  }
}

async function loadOpacity() {
  try {
    opacity.value = await getAdminBgOpacity()
  } catch (e) {
    // 读取失败时保持默认不透明
  }
}

onMounted(() => {
  load()
  loadOpacity()
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
.head-ops {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tip {
  margin: 0 0 14px;
  color: #909399;
  font-size: 13px;
}
.opacity-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.opacity-label {
  flex-shrink: 0;
  color: #606266;
  font-size: 14px;
}
.opacity-slider {
  flex: 1;
  min-width: 180px;
}
.bg-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.bg-card {
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  overflow: hidden;
}
.preview {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #f5f7fa;
}
.preview img,
.preview video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.badges {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.tag {
  padding: 2px 8px;
  font-size: 12px;
  color: #fff;
  border-radius: 999px;
}
.tag.portal {
  background: #409eff;
}
.tag.admin {
  background: #e6a23c;
}
.info {
  padding: 10px;
}
.name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 8px;
}
.ops {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.empty {
  grid-column: 1 / -1;
}
</style>
