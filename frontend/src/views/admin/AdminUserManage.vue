<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>管理员管理</span>
        <el-button type="primary" @click="openRegister">注册管理员</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.role === 'SUPER' ? 'danger' : row.role === 'ADMIN1' ? 'primary' : 'info'">
            {{ roleLabel(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="菜单权限" min-width="220">
        <template #default="{ row }">
          <span v-if="row.role === 'SUPER'" class="all-menus">全部菜单</span>
          <template v-else>
            <el-tag v-for="m in row.menus" :key="m" size="small" class="menu-tag">{{ menuTitle(m) }}</el-tag>
            <span v-if="!row.menus || !row.menus.length" class="muted">未配置</span>
          </template>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.role !== 'SUPER'">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑管理员' : '注册管理员'" width="min(92vw, 520px)">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录用户名" maxlength="50" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空则不修改密码' : '至少 8 位'" maxlength="100" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="显示昵称" maxlength="50" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio value="ADMIN1">一级管理员</el-radio>
            <el-radio value="ADMIN2">二级管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单权限">
          <el-checkbox-group v-model="form.menus" class="menu-check">
            <el-checkbox v-for="m in grantableMenus" :key="m.key" :value="m.key">{{ m.title }}</el-checkbox>
          </el-checkbox-group>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminUserList, registerAdmin, updateAdmin, deleteAdmin } from '@/api/user'
import { adminMenus } from '@/config/menus'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, username: '', password: '', nickname: '', role: 'ADMIN1', menus: [] })

// 授权时只展示叶子菜单，父菜单（如 API 管理）只作为侧边栏分组，不单独授权
const grantableMenus = adminMenus
  .flatMap((m) => (m.children ? m.children : [m]))
  .filter((m) => !m.superOnly)

function roleLabel(role) {
  if (role === 'SUPER') return '超级管理员'
  if (role === 'ADMIN2') return '二级管理员'
  return '一级管理员'
}

function menuTitle(key) {
  for (const m of adminMenus) {
    if (m.key === key) return m.title
    if (m.children) {
      const c = m.children.find((x) => x.key === key)
      if (c) return c.title
    }
  }
  return key
}

async function load() {
  loading.value = true
  try {
    list.value = await adminUserList()
  } finally {
    loading.value = false
  }
}

function openRegister() {
  form.id = null
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.role = 'ADMIN1'
  form.menus = []
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.username = row.username
  form.password = ''
  form.nickname = row.nickname
  form.role = row.role
  form.menus = [...(row.menus || [])]
  dialogVisible.value = true
}

async function onSave() {
  if (!form.id) {
    if (!form.username.trim()) {
      ElMessage.warning('请输入用户名')
      return
    }
    if (!form.password || form.password.length < 8) {
      ElMessage.warning('密码至少 8 位')
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      id: form.id,
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim(),
      role: form.role,
      menus: form.menus
    }
    if (form.id) {
      await updateAdmin(payload)
    } else {
      await registerAdmin(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除管理员「${row.username}」吗？`, '删除管理员', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteAdmin(row.id)
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
.all-menus {
  color: #67c23a;
  font-size: 13px;
}
.muted {
  color: #909399;
  font-size: 13px;
}
.menu-tag {
  margin: 2px 6px 2px 0;
}
.menu-check {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 0;
}
</style>
