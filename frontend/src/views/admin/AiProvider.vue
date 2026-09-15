<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>AI 服务商配置</span>
        <el-button type="primary" @click="openEdit()">新增服务商</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column label="归属" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.ownerId ? 'primary' : 'warning'">
            {{ row.ownerId ? '我的' : '系统' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="baseUrl" label="接口地址" min-width="220" show-overflow-tooltip />
      <el-table-column label="API Key" width="100">
        <template #default="{ row }">
          <el-tag :type="row.apiKey ? 'success' : 'info'">{{ row.apiKey ? '已配置' : '未配置' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault === 1" type="warning">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" @click="onModels(row)">获取模型</el-button>
          <el-button v-if="row.isDefault !== 1" size="small" type="primary" @click="onDefault(row)">设为默认</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑服务商' : '新增服务商'" width="min(92vw, 480px)">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如 DeepSeek、gcli" maxlength="100" />
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input v-model="form.baseUrl" placeholder="如 https://gcli.ggchan.dev 或 https://api.deepseek.com" maxlength="300" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." maxlength="500" />
        </el-form-item>
      </el-form>
      <p class="tip">接口需兼容 OpenAI 风格；若需要 /v1 路径，请把 /v1 一并写在接口地址里。</p>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="modelsVisible" :title="`模型列表 - ${modelsProviderName}`" width="min(92vw, 420px)">
      <div v-loading="modelsLoading">
        <el-tag v-for="m in models" :key="m" class="model-tag">{{ m }}</el-tag>
        <el-empty v-if="!modelsLoading && !models.length" description="没有获取到模型" :image-size="60" />
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiProviderList, saveAiProvider, deleteAiProvider, setDefaultAiProvider, aiProviderModels } from '@/api/ai'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const form = reactive({ id: null, name: '', baseUrl: '', apiKey: '' })

const modelsVisible = ref(false)
const modelsLoading = ref(false)
const models = ref([])
const modelsProviderName = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await aiProviderList()
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  form.id = row ? row.id : null
  form.name = row ? row.name : ''
  form.baseUrl = row ? row.baseUrl : ''
  form.apiKey = row ? row.apiKey : ''
  editVisible.value = true
}

async function onSave() {
  if (!form.name.trim() || !form.baseUrl.trim()) {
    ElMessage.warning('请填写名称和接口地址')
    return
  }
  saving.value = true
  try {
    await saveAiProvider({ id: form.id, name: form.name.trim(), baseUrl: form.baseUrl.trim(), apiKey: form.apiKey.trim() })
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDefault(row) {
  await setDefaultAiProvider(row.id)
  ElMessage.success('已设为默认')
  load()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除服务商「${row.name}」吗？`, '删除服务商', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteAiProvider(row.id)
  ElMessage.success('删除成功')
  load()
}

async function onModels(row) {
  modelsVisible.value = true
  modelsProviderName.value = row.name
  modelsLoading.value = true
  models.value = []
  try {
    models.value = await aiProviderModels(row.id)
  } finally {
    modelsLoading.value = false
  }
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
.tip {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}
.model-tag {
  margin: 4px 6px 4px 0;
}
</style>
