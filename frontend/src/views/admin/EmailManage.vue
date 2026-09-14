<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>邮件管理（QQ 邮箱 SMTP）</span>
      </div>
    </template>

    <el-tabs v-model="tab">
      <!-- 邮件配置 -->
      <el-tab-pane label="邮件配置" name="config">
        <el-form :model="config" label-width="120px" class="config-form">
          <el-form-item label="QQ 邮箱账号">
            <el-input v-model="config.username" placeholder="如：xxxxx@qq.com" maxlength="100" />
          </el-form-item>
          <el-form-item label="邮箱授权码">
            <el-input v-model="config.authCode" type="password" show-password placeholder="QQ 邮箱设置里生成的授权码" maxlength="100" />
          </el-form-item>
          <el-form-item label="发件人昵称">
            <el-input v-model="config.senderName" placeholder="如：bcBlog" maxlength="50" />
          </el-form-item>
          <el-form-item label="SMTP 服务器">
            <el-input v-model="config.host" placeholder="smtp.qq.com" maxlength="100" />
          </el-form-item>
          <el-form-item label="SMTP 端口">
            <el-input-number v-model="config.port" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="使用 SSL">
            <el-switch v-model="config.ssl" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="configSaving" @click="onSaveConfig">保存配置</el-button>
          </el-form-item>
        </el-form>

        <el-divider content-position="left">发送测试</el-divider>
        <el-form :model="testForm" label-width="120px" class="config-form">
          <el-form-item label="收件邮箱">
            <el-input v-model="testForm.to" placeholder="测试收件邮箱" maxlength="100" />
          </el-form-item>
          <el-form-item label="使用模板">
            <el-select v-model="testForm.scenario" style="width: 100%">
              <el-option v-for="s in scenarioOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :loading="testing" @click="onTest">发送测试邮件</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 邮件模板 -->
      <el-tab-pane label="邮件模板" name="template">
        <div class="template-head">
          <el-select v-model="scenarioFilter" clearable placeholder="按场景筛选" class="scenario-filter" @change="() => {}">
            <el-option v-for="s in scenarioOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button type="primary" @click="openEdit()">新增模板</el-button>
        </div>
        <el-table :data="filteredTemplates" v-loading="loading">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="scenario" label="场景编码" width="160" />
          <el-table-column prop="name" label="模板名称" min-width="140" />
          <el-table-column prop="subject" label="邮件主题" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前使用" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.active === 1" type="warning">当前</el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button v-if="row.active !== 1" size="small" type="primary" plain @click="onActivate(row)">启用</el-button>
              <el-button size="small" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 模板编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑邮件模板' : '新增邮件模板'" width="min(94vw, 760px)">
      <el-form :model="form" label-width="110px">
        <el-form-item label="场景编码">
          <el-select
            v-model="form.scenario"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入场景编码"
            style="width: 100%"
            :disabled="!!form.id"
          >
            <el-option v-for="s in scenarioOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="form.name" placeholder="如 注册验证码" maxlength="100" />
        </el-form-item>
        <el-form-item label="邮件主题">
          <el-input v-model="form.subject" placeholder="支持 {{siteName}} 变量" maxlength="200" />
        </el-form-item>
        <el-form-item label="背景图片">
          <div class="bg-row">
            <el-input v-model="form.backgroundImage" placeholder="背景图地址，可留空" maxlength="500" />
            <el-upload
              :action="'/api/admin/upload/image'"
              :headers="uploadHeaders"
              :show-file-list="false"
              accept="image/*"
              :on-success="onBgSuccess"
              :on-error="onBgError"
            >
              <el-button>上传背景</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="卡片透明度">
          <el-slider v-model="form.overlayOpacity" :min="0.1" :max="1" :step="0.05" />
        </el-form-item>
        <el-form-item label="可用变量">
          <el-input v-model="form.variables" placeholder="英文逗号分隔，如 siteName,code,email" maxlength="500" />
        </el-form-item>
        <el-form-item label="正文 HTML">
          <el-input v-model="form.contentHtml" type="textarea" :rows="10" placeholder="支持 HTML 和 {{变量}}" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="设为当前使用">
          <el-switch v-model="form.active" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="预览">
          <div class="preview" v-html="previewHtml"></div>
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
import {
  getEmailConfig,
  saveEmailConfig,
  emailTemplates,
  saveEmailTemplate,
  deleteEmailTemplate,
  activateEmailTemplate,
  testEmail
} from '@/api/email'
import { getConfig } from '@/api/config'

const tab = ref('config')
const loading = ref(false)
const saving = ref(false)
const configSaving = ref(false)
const testing = ref(false)
const dialogVisible = ref(false)
const templates = ref([])
const siteName = ref('bcBlog')
const scenarioFilter = ref('')
const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }

const config = reactive({ username: '', authCode: '', senderName: 'bcBlog', host: 'smtp.qq.com', port: 465, ssl: 1 })
const testForm = reactive({ to: '', scenario: 'register_code' })
const form = reactive({
  id: null,
  scenario: '',
  name: '',
  subject: '',
  backgroundImage: '',
  overlayOpacity: 0.9,
  contentHtml: '',
  variables: '',
  enabled: 1,
  active: 0
})

const KNOWN_SCENARIOS = [
  { value: 'register_code', label: '注册验证码（register_code）' }
]

const scenarioOptions = computed(() => {
  const map = new Map()
  KNOWN_SCENARIOS.forEach((s) => map.set(s.value, s.label))
  templates.value.forEach((t) => {
    if (!map.has(t.scenario)) map.set(t.scenario, t.scenario)
  })
  return Array.from(map, ([value, label]) => ({ value, label }))
})

const filteredTemplates = computed(() => {
  if (!scenarioFilter.value) return templates.value
  return templates.value.filter((t) => t.scenario === scenarioFilter.value)
})

const previewHtml = computed(() => {
  const raw = form.contentHtml || ''
  const content = raw
    .replace(/\{\{siteName\}\}/g, siteName.value)
    .replace(/\{\{code\}\}/g, '123456')
    .replace(/\{\{email\}\}/g, 'user@example.com')
    .replace(/\{\{nickname\}\}/g, '测试用户')
  // 完整 HTML 模板直接预览
  if (/<!doctype|<html/i.test(raw)) {
    return content
  }
  const bg = form.backgroundImage ? `background-image:url('${form.backgroundImage}');background-size:cover;background-position:center;` : ''
  const opacity = Math.max(0.1, Math.min(1, form.overlayOpacity || 0.9))
  return `<div style="background-color:#f5f6fa;padding:24px;${bg}">
    <div style="max-width:600px;margin:0 auto;background:rgba(255,255,255,${opacity});border-radius:16px;padding:28px;">${content}</div>
  </div>`
})

async function loadConfig() {
  const data = await getEmailConfig()
  Object.assign(config, data)
}

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await emailTemplates()
    if (templates.value.length && !testForm.scenario) {
      testForm.scenario = templates.value[0].scenario
    }
  } finally {
    loading.value = false
  }
}

async function onSaveConfig() {
  if (!config.username.trim() || !config.authCode.trim()) {
    ElMessage.warning('请填写 QQ 邮箱账号和授权码')
    return
  }
  configSaving.value = true
  try {
    await saveEmailConfig({ ...config })
    ElMessage.success('邮件配置已保存')
  } finally {
    configSaving.value = false
  }
}

async function onTest() {
  if (!testForm.to.trim()) {
    ElMessage.warning('请输入测试收件邮箱')
    return
  }
  testing.value = true
  try {
    await testEmail({ to: testForm.to.trim(), scenario: testForm.scenario })
    ElMessage.success('测试邮件已发送')
  } finally {
    testing.value = false
  }
}

function openEdit(row) {
  form.id = row ? row.id : null
  form.scenario = row ? row.scenario : ''
  form.name = row ? row.name : ''
  form.subject = row ? row.subject : ''
  form.backgroundImage = row ? row.backgroundImage || '' : ''
  form.overlayOpacity = row ? Number(row.overlayOpacity || 0.9) : 0.9
  form.contentHtml = row ? row.contentHtml || '' : ''
  form.variables = row ? row.variables || '' : ''
  form.enabled = row ? (row.enabled === 0 ? 0 : 1) : 1
  form.active = row ? (row.active === 1 ? 1 : 0) : 0
  dialogVisible.value = true
}

function onBgSuccess(res) {
  if (res && res.code === 200 && res.data) {
    form.backgroundImage = res.data
    ElMessage.success('背景上传成功')
  } else {
    ElMessage.error((res && res.msg) || '背景上传失败')
  }
}

function onBgError() {
  ElMessage.error('背景上传失败')
}

async function onSave() {
  if (!form.scenario.trim() || !form.name.trim() || !form.subject.trim()) {
    ElMessage.warning('请填写场景编码、模板名称和邮件主题')
    return
  }
  saving.value = true
  try {
    await saveEmailTemplate({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadTemplates()
  } finally {
    saving.value = false
  }
}

async function onActivate(row) {
  await activateEmailTemplate(row.id)
  ElMessage.success(`已切换为「${row.name}」`)
  loadTemplates()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.name}」吗？`, '删除模板', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteEmailTemplate(row.id)
  ElMessage.success('删除成功')
  loadTemplates()
}

onMounted(() => {
  loadConfig()
  loadTemplates()
  getConfig().then((config) => {
    siteName.value = config.siteName || 'bcBlog'
  }).catch(() => {})
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.config-form {
  max-width: 560px;
}
.template-head {
  margin-bottom: 12px;
}
.bg-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.preview {
  width: 100%;
  max-height: 320px;
  overflow: auto;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
}
</style>
