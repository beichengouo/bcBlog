<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>看板娘管理</span>
        <div class="toolbar-right">
          <span class="enable-label">前台显示</span>
          <el-switch :model-value="enabled" @change="onToggleEnabled" />
          <el-button type="primary" @click="openAdd">新增模型</el-button>
        </div>
      </div>
    </template>

    <el-alert
      class="tips"
      type="info"
      :closable="false"
      title="模型地址建议填写 https://model.hacxy.cn/.../model.json，可在 model.hacxy.cn 目录中挑选角色。"
    />

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="角色名称" min-width="130" />
      <el-table-column prop="description" label="角色说明" min-width="170" show-overflow-tooltip />
      <el-table-column prop="modelKey" label="标识" width="110" />
      <el-table-column prop="url" label="模型地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.active === 1 ? 'success' : 'info'">
            {{ row.active === 1 ? '使用中' : '未使用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="row.active !== 1" size="small" type="primary" @click="onActive(row)">启用</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新增模型" width="min(92vw, 520px)">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色名称">
          <el-input v-model="form.name" placeholder="如：蕾姆 / 小埋" maxlength="100" />
        </el-form-item>
        <el-form-item label="角色说明">
          <el-input v-model="form.description" placeholder="一句话说明角色风格" maxlength="255" />
        </el-form-item>
        <el-form-item label="模型地址">
          <el-input v-model="form.url" placeholder="https://model.hacxy.cn/rem/model.json" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAdd">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { live2dModelList, addLive2dModel, deleteLive2dModel, setActiveLive2dModel } from '@/api/live2d'
import { getConfig, setLive2dEnabled } from '@/api/config'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const enabled = ref(true)
const form = reactive({ name: '', description: '', url: '', sortOrder: 0 })

async function load() {
  loading.value = true
  try {
    list.value = await live2dModelList()
  } finally {
    loading.value = false
  }
}

async function loadEnabled() {
  try {
    const config = await getConfig()
    enabled.value = config.live2dEnabled !== 0
  } catch (e) {
    enabled.value = true
  }
}

async function onToggleEnabled(val) {
  await setLive2dEnabled(val)
  enabled.value = val
  ElMessage.success(val ? '已开启前台看板娘' : '已关闭前台看板娘')
}

function openAdd() {
  form.name = ''
  form.description = ''
  form.url = ''
  form.sortOrder = 0
  dialogVisible.value = true
}

async function onAdd() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入角色名称')
    return
  }
  if (!/^https?:\/\//.test(form.url.trim())) {
    ElMessage.warning('模型地址必须以 http(s) 开头')
    return
  }
  saving.value = true
  try {
    await addLive2dModel({ ...form, name: form.name.trim(), url: form.url.trim() })
    ElMessage.success('新增成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onActive(row) {
  await setActiveLive2dModel(row.id)
  ElMessage.success('已切换为该看板娘')
  load()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '删除模型', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteLive2dModel(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  load()
  loadEnabled()
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
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.enable-label {
  font-size: 13px;
  color: var(--text-muted);
}
.tips {
  margin-bottom: 14px;
}
</style>
