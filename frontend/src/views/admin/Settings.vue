<template>
  <el-card class="settings-card">
    <template #header>系统设置</template>
    <el-form :model="form" label-width="120px">
      <el-form-item label="站点名称">
        <el-input v-model="form.siteName" placeholder="站点名称" maxlength="100" />
      </el-form-item>
      <el-form-item label="站点 Logo">
        <div class="logo-box">
          <el-avatar :size="64" shape="circle" :src="form.siteLogo" class="logo-preview">
            <span>Logo</span>
          </el-avatar>
          <div class="logo-actions">
            <el-button size="small" @click="onPickLogo">上传图片</el-button>
            <el-button size="small" type="danger" plain :disabled="!form.siteLogo" @click="onDeleteLogo">
              删除恢复默认
            </el-button>
            <span class="logo-tip">支持 jpg / png / gif / webp，建议使用正方形图片</span>
          </div>
          <input ref="logoInput" type="file" accept="image/*" hidden @change="onLogoChange" />
        </div>
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
      <el-form-item label="首页标语">
        <el-input v-model="form.siteSlogan" placeholder="首页标题下方轮播语，如：愿每一次点击都有温度" maxlength="100" />
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
import { getConfig, saveConfig, uploadSiteLogo, deleteSiteLogo } from '@/api/config'
import { applySiteMeta } from '@/utils/siteMeta'

const saving = ref(false)
const logoInput = ref()
const form = reactive({
  siteName: '',
  siteLogo: '',
  siteIcp: '',
  siteDescription: '',
  siteKeywords: '',
  siteSlogan: ''
})

async function load() {
  const data = await getConfig()
  form.siteName = data.siteName || ''
  form.siteLogo = data.siteLogo || ''
  form.siteIcp = data.siteIcp || ''
  form.siteDescription = data.siteDescription || ''
  form.siteKeywords = data.siteKeywords || ''
  form.siteSlogan = data.siteSlogan || ''
}

async function onSave() {
  saving.value = true
  try {
    await saveConfig({ ...form })
    // 保存后立即同步浏览器标签页名称和 Logo
    applySiteMeta({ ...form })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

function onPickLogo() {
  logoInput.value?.click()
}

async function onLogoChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) {
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('Logo 图片大小不能超过 5MB')
    return
  }
  const url = await uploadSiteLogo(file)
  form.siteLogo = url
  applySiteMeta({ ...form })
  ElMessage.success('Logo 已更新，点击“保存”可同步其他设置')
}

async function onDeleteLogo() {
  await deleteSiteLogo()
  form.siteLogo = '/uploads/logo/avatar.png'
  applySiteMeta({ ...form })
  ElMessage.success('已恢复默认 Logo')
}

onMounted(load)
</script>

<style scoped>
.settings-card {
  max-width: 640px;
}
.logo-box {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  flex-wrap: wrap;
}
.logo-preview {
  flex-shrink: 0;
  border: 1px solid var(--border);
  background: var(--glass-bg);
}
.logo-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.logo-tip {
  font-size: 12px;
  color: var(--text-muted);
}
</style>
