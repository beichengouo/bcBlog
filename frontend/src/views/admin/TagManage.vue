<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>标签管理</span>
        <el-button type="primary" @click="openAdd">新增标签</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="标签名称" min-width="160" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑标签' : '新增标签'" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标签名称">
          <el-input v-model="form.name" placeholder="请输入标签名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tagList, saveTag, updateTag, deleteTag } from '@/api/tag'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, name: '' })

async function load() {
  loading.value = true
  try {
    list.value = await tagList()
  } finally {
    loading.value = false
  }
}

function openAdd() {
  form.id = null
  form.name = ''
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  const payload = { id: form.id, name: form.name.trim() }
  if (form.id) {
    await updateTag(payload)
  } else {
    await saveTag(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除标签「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteTag(row.id)
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
