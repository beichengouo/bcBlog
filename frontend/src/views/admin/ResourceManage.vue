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
const form = reactive({ id: null, title: '', description: '', url: '', password: '' })

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
  form.url = row ? row.url : ''
  form.password = row ? row.password || '' : ''
  dialogVisible.value = true
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
</style>
