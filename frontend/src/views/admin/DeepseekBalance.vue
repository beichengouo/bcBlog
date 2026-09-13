<template>
  <div class="deepseek-page">
    <el-card class="block">
      <template #header>DeepSeek API 配置</template>
      <div class="key-form">
        <el-input
          v-model="apiKey"
          type="password"
          show-password
          placeholder="请输入 DeepSeek API Key（sk-...）"
          maxlength="200"
          class="key-input"
        />
        <el-button type="primary" :loading="saving" @click="onSaveKey">保存</el-button>
      </div>
      <p class="tip">API Key 仅保存在本机数据库的 sys_config 表中，请勿将 Key 提交到公开仓库。</p>
    </el-card>

    <el-card class="block">
      <template #header>
        <div class="balance-header">
          <span>账户余额</span>
          <el-button type="primary" :loading="loading" @click="loadBalance">查询余额</el-button>
        </div>
      </template>

      <el-alert
        v-if="balance"
        :type="balance.available ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="balance.available ? '账户可用' : '账户不可用（可能余额不足或未激活）'"
        class="status"
      />

      <el-table v-if="balance" :data="balance.balanceInfos || []" border>
        <el-table-column prop="currency" label="币种" width="120" />
        <el-table-column prop="totalBalance" label="总余额" min-width="140" />
        <el-table-column prop="grantedBalance" label="赠送余额" min-width="140" />
        <el-table-column prop="toppedUpBalance" label="充值余额" min-width="140" />
      </el-table>

      <el-empty v-if="!balance && !loading" description="暂无余额数据，请先查询" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getApiKey, saveApiKey, getBalance } from '@/api/deepseek'

const apiKey = ref('')
const saving = ref(false)
const loading = ref(false)
const balance = ref(null)

async function loadApiKey() {
  const key = await getApiKey()
  apiKey.value = key || ''
}

async function onSaveKey() {
  if (!apiKey.value.trim()) {
    ElMessage.warning('请先输入 API Key')
    return
  }
  saving.value = true
  try {
    await saveApiKey(apiKey.value.trim())
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

async function loadBalance() {
  loading.value = true
  try {
    balance.value = await getBalance()
  } finally {
    loading.value = false
  }
}

onMounted(loadApiKey)
</script>

<style scoped>
.deepseek-page {
  max-width: 720px;
}
.block {
  margin-bottom: 16px;
}
.key-form {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.key-input {
  flex: 1;
  min-width: 260px;
}
.tip {
  margin: 12px 0 0;
  color: #909399;
  font-size: 13px;
}
.balance-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.status {
  margin-bottom: 12px;
}
</style>
