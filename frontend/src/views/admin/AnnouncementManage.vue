<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>站点公告管理</span>
        <el-button type="primary" @click="openAdd">新增公告</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="content" label="公告内容" min-width="260" show-overflow-tooltip />
      <el-table-column prop="author" label="发布人" width="120" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled === 1"
            @change="(val) => onToggle(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑公告' : '新增公告'" width="min(92vw, 560px)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="公告内容">
          <el-input v-model="form.content" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="输入要展示的公告内容" />
        </el-form-item>
        <el-form-item label="发布人">
          <el-input v-model="form.author" placeholder="发布人，默认管理员" maxlength="50" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { announcementList, addAnnouncement, updateAnnouncement, deleteAnnouncement } from '@/api/announcement'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, content: '', author: '管理员', sortOrder: 0, enabled: 1 })

async function load() {
  loading.value = true
  try {
    list.value = await announcementList()
  } finally {
    loading.value = false
  }
}

function openAdd() {
  form.id = null
  form.content = ''
  form.author = '管理员'
  form.sortOrder = 0
  form.enabled = 1
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.content = row.content
  form.author = row.author || '管理员'
  form.sortOrder = row.sortOrder
  form.enabled = row.enabled
  dialogVisible.value = true
}

async function onSave() {
  if (!form.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateAnnouncement({ ...form, content: form.content.trim(), author: form.author.trim() })
    } else {
      await addAnnouncement({ ...form, content: form.content.trim(), author: form.author.trim() })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onToggle(row, val) {
  await updateAnnouncement({ ...row, enabled: val ? 1 : 0 })
  ElMessage.success(val ? '已发布' : '已停用')
  load()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除这条公告吗？', '删除公告', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteAnnouncement(row.id)
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
