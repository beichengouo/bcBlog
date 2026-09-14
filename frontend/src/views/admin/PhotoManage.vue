<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>流光忆庭（照片墙）</span>
        <el-button type="primary" @click="openEdit()">上传照片</el-button>
      </div>
    </template>

    <el-empty v-if="!loading && !list.length" description="还没有照片，点击右上角上传" />
    <div v-else class="photo-grid" v-loading="loading">
      <div v-for="p in list" :key="p.id" class="photo-card">
        <div class="preview">
          <img :src="p.url" alt="" />
        </div>
        <div class="info">
          <div class="title">{{ p.title || '未命名照片' }}</div>
          <div v-if="p.description" class="desc">{{ p.description }}</div>
          <div class="ops">
            <el-button size="small" @click="openEdit(p)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDelete(p)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑照片' : '上传照片'" width="min(92vw, 520px)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="照片">
          <el-upload
            :action="'/api/admin/upload/image'"
            :headers="uploadHeaders"
            :show-file-list="false"
            accept="image/*"
            :on-success="onUploadSuccess"
            :on-error="onUploadError"
          >
            <img v-if="form.url" :src="form.url" class="upload-preview" alt="" />
            <el-button v-else>选择图片</el-button>
          </el-upload>
          <span class="tip">支持 jpg / png / gif / webp，单张不超过 5MB</span>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="照片标题（可选）" maxlength="200" />
        </el-form-item>
        <el-form-item label="介绍">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="简单介绍一下这张照片" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { photoList, savePhoto, deletePhoto } from '@/api/photo'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }
const form = reactive({ id: null, title: '', description: '', url: '' })

async function load() {
  loading.value = true
  try {
    list.value = await photoList()
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  form.id = row ? row.id : null
  form.title = row ? row.title || '' : ''
  form.description = row ? row.description || '' : ''
  form.url = row ? row.url || '' : ''
  dialogVisible.value = true
}

function onUploadSuccess(res) {
  if (res && res.code === 200 && res.data) {
    form.url = res.data
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.error((res && res.msg) || '图片上传失败')
  }
}

function onUploadError() {
  ElMessage.error('图片上传失败')
}

async function onSave() {
  if (!form.url) {
    ElMessage.warning('请先选择图片')
    return
  }
  saving.value = true
  try {
    await savePhoto({ id: form.id, title: form.title, description: form.description, url: form.url })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除这张照片吗？', '删除照片', { type: 'warning' })
  } catch (e) {
    return
  }
  await deletePhoto(row.id)
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
.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.photo-card {
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  overflow: hidden;
}
.preview {
  aspect-ratio: 4 / 3;
  background: #f5f7fa;
}
.preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.info {
  padding: 10px 12px;
}
.title {
  font-weight: 600;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.desc {
  color: #909399;
  font-size: 13px;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ops {
  display: flex;
  gap: 8px;
}
.upload-preview {
  max-height: 120px;
  border-radius: 8px;
  display: block;
}
.tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>
