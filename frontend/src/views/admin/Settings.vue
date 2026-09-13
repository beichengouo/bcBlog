<template>
  <el-card class="settings-card">
    <template #header>系统设置</template>
    <el-form :model="form" label-width="120px">
      <el-form-item label="站点名称">
        <el-input v-model="form.siteName" placeholder="站点名称" maxlength="100" />
      </el-form-item>
      <el-form-item label="站点 Logo">
        <el-input v-model="form.siteLogo" placeholder="Logo 图片地址" maxlength="255" />
      </el-form-item>
      <el-form-item label="备案号">
        <el-input v-model="form.siteIcp" placeholder="如：京ICP备xxxxxx号" maxlength="100" />
      </el-form-item>
      <el-form-item label="SEO 描述">
        <el-input v-model="form.siteDescription" type="textarea" :rows="2" placeholder="站点描述" maxlength="300" />
      </el-form-item>
      <el-form-item label="SEO 关键词">
        <el-input v-model="form.siteKeywords" placeholder="关键词，英文逗号分隔" maxlength="200" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfig, saveConfig } from '@/api/config'

const saving = ref(false)
const form = reactive({
  siteName: '',
  siteLogo: '',
  siteIcp: '',
  siteDescription: '',
  siteKeywords: ''
})

async function load() {
  const data = await getConfig()
  form.siteName = data.siteName || ''
  form.siteLogo = data.siteLogo || ''
  form.siteIcp = data.siteIcp || ''
  form.siteDescription = data.siteDescription || ''
  form.siteKeywords = data.siteKeywords || ''
}

async function onSave() {
  saving.value = true
  try {
    await saveConfig({ ...form })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.settings-card {
  max-width: 640px;
}
</style>
