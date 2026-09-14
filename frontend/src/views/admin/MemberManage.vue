<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>普通用户管理</span>
        <el-input v-model="keyword" placeholder="搜索用户名 / 昵称 / 邮箱" clearable class="search" @keyup.enter="load" />
      </div>
    </template>

    <el-table :data="filteredList" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column label="等级" width="120">
        <template #default="{ row }">Lv.{{ row.level || 1 }} · {{ row.exp || 0 }} EXP</template>
      </el-table-column>
      <el-table-column prop="points" label="积分" width="90" />
      <el-table-column label="邀请权限" width="100">
        <template #default="{ row }">
          <el-tag :type="row.canInvite === 1 ? 'success' : 'info'">{{ row.canInvite === 1 ? '有' : '无' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="170" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="编辑用户" width="min(92vw, 480px)">
      <el-form :model="form" label-width="100px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" disabled />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="50" />
        </el-form-item>
        <el-form-item label="重置密码">
          <el-input v-model="form.password" type="password" show-password placeholder="留空则不修改" maxlength="100" />
        </el-form-item>
        <el-form-item label="邀请码权限">
          <el-switch v-model="form.canInvite" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="账号状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { memberPage, updateMember, deleteMember } from '@/api/member'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const dialogVisible = ref(false)
const form = reactive({ id: null, username: '', nickname: '', password: '', canInvite: 0, status: 1 })

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((u) =>
    (u.username || '').toLowerCase().includes(kw) ||
    (u.nickname || '').toLowerCase().includes(kw) ||
    (u.email || '').toLowerCase().includes(kw)
  )
})

async function load() {
  loading.value = true
  try {
    list.value = await memberPage()
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  form.id = row.id
  form.username = row.username
  form.nickname = row.nickname || ''
  form.password = ''
  form.canInvite = row.canInvite === 1 ? 1 : 0
  form.status = row.status === 1 ? 1 : 0
  dialogVisible.value = true
}

async function onSave() {
  saving.value = true
  try {
    await updateMember({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '删除用户', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteMember(row.id)
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
.search {
  width: 240px;
}
</style>
