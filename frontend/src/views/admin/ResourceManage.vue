<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>智库（资源区）</span>
        <el-button type="primary" @click="openEdit()">新增资源</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="资源名称" min-width="160" />
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column label="封面" width="90">
        <template #default="{ row }">
          <img v-if="row.cover" class="cover-thumb" :src="row.cover" alt="" />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="points" label="所需积分" width="100" />
      <el-table-column prop="url" label="链接" min-width="220" show-overflow-tooltip />
      <el-table-column prop="password" label="密码" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑资源' : '新增资源'" width="min(92vw, 520px)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.title" placeholder="如：某某资源合集" maxlength="200" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="资源简单说明" maxlength="500" />
        </el-form-item>
        <el-form-item label="封面">
          <el-upload
            :action="'/api/admin/upload/image'"
            :headers="uploadHeaders"
            :show-file-list="false"
            accept="image/*"
            :on-success="onCoverSuccess"
            :on-error="onCoverError"
          >
            <img v-if="form.cover" class="cover-preview" :src="form.cover" alt="" />
            <el-button v-else>上传封面</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="所需积分">
          <el-input-number v-model="form.points" :min="0" :max="100000" />
          <span class="tip">前往资源时扣除的积分，0 表示免费</span>
        </el-form-item>
        <el-form-item label="资源详情">
          <el-input v-model="form.content" type="textarea" :rows="8" placeholder="资源详情内容，支持 HTML，前台按文章样式展示" />
        </el-form-item>
        <el-form-item label="链接">
          <el-input v-model="form.url" placeholder="如：https://pan.baidu.com/s/xxxx" maxlength="500" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" placeholder="网盘提取码，可为空" maxlength="100" />
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
import { resourceList, saveResource, deleteResource } from '@/api/resource'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }
const form = reactive({ id: null, title: '', description: '', cover: '', points: 1, content: '', url: '', password: '' })

async function load() {
  loading.value = true
  try {
    list.value = await resourceList()
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  form.id = row ? row.id : null
  form.title = row ? row.title : ''
  form.description = row ? row.description || '' : ''
  form.cover = row ? row.cover || '' : ''
  form.points = row && row.points != null ? row.points : 1
  form.content = row ? row.content || '' : ''
  form.url = row ? row.url : ''
  form.password = row ? row.password || '' : ''
  dialogVisible.value = true
}

function onCoverSuccess(res) {
  if (res && res.code === 200 && res.data) {
    form.cover = res.data
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.error((res && res.msg) || '封面上传失败')
  }
}

function onCoverError() {
  ElMessage.error('封面上传失败')
}

async function onSave() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入资源名称')
    return
  }
  if (!form.url.trim()) {
    ElMessage.warning('请输入资源链接')
    return
  }
  saving.value = true
  try {
    await saveResource({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除资源「${row.title}」吗？`, '删除资源', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteResource(row.id)
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
.cover-thumb {
  width: 56px;
  height: 36px;
  object-fit: cover;
  border-radius: 6px;
  display: block;
}
.cover-preview {
  max-height: 100px;
  border-radius: 8px;
  display: block;
}
.tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.muted {
  color: #909399;
}
</style>
