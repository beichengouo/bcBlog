<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>登录日志</span>
        <div class="filter">
          <el-input v-model="username" placeholder="按用户名搜索" clearable class="search" @keyup.enter="reload" />
          <el-date-picker
            v-model="startTime"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="开始时间"
            class="time"
          />
          <el-date-picker
            v-model="endTime"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="结束时间"
            class="time"
          />
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button type="danger" :loading="clearing" @click="onClearLogs">清空日志</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="ip" label="IP" width="150" />
      <el-table-column label="位置" width="110">
        <template #default="{ row }">
          <el-button size="small" text type="primary" :loading="ipLoading && currentIp === row.ip" @click="onQueryIp(row.ip)">
            查询位置
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="说明" width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="170" />
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      class="pager"
      @current-change="load"
    />

    <el-dialog v-model="ipDialogVisible" title="IP 位置查询" width="min(92vw, 420px)">
      <div class="ip-dialog">
        <div class="ip-dialog-row">
          <span class="label">IP 地址</span>
          <span>{{ currentIp }}</span>
        </div>
        <div class="ip-dialog-row">
          <span class="label">位置结果</span>
          <span v-if="ipResult">{{ ipResult }}</span>
          <span v-else class="muted">暂无结果</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="ipDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { loginLogPage, clearLoginLogs, queryIpLocation } from '@/api/log'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const username = ref('')
const startTime = ref('')
const endTime = ref('')
const loading = ref(false)
const clearing = ref(false)
const ipDialogVisible = ref(false)
const ipLoading = ref(false)
const ipResult = ref('')
const currentIp = ref('')

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (username.value) {
      params.username = username.value
    }
    if (startTime.value) {
      params.startTime = startTime.value
    }
    if (endTime.value) {
      params.endTime = endTime.value
    }
    const data = await loginLogPage(params)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

async function onClearLogs() {
  // 若未选择时间范围则清空全部日志，需要二次确认
  const rangeText = startTime.value || endTime.value
    ? `${startTime.value || '不限'} ~ ${endTime.value || '不限'}`
    : '全部'
  try {
    await ElMessageBox.confirm(
      `确定要清空「${rangeText}」时间范围内的登录日志吗？此操作不可恢复。`,
      '清空登录日志',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }

  clearing.value = true
  try {
    const params = {}
    if (startTime.value) {
      params.startTime = startTime.value
    }
    if (endTime.value) {
      params.endTime = endTime.value
    }
    const count = await clearLoginLogs(params)
    ElMessage.success(`已清空 ${count} 条日志`)
    reload()
  } finally {
    clearing.value = false
  }
}

async function onQueryIp(ip) {
  currentIp.value = ip
  ipResult.value = ''
  ipDialogVisible.value = true
  ipLoading.value = true
  try {
    ipResult.value = await queryIpLocation(ip)
  } catch (e) {
    ipResult.value = '查询失败'
  } finally {
    ipLoading.value = false
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
.filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.search {
  width: 200px;
}
.time {
  width: 180px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.ip-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ip-dialog-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.ip-dialog-row .label {
  flex-shrink: 0;
  width: 70px;
  color: #909399;
}
.muted {
  color: #909399;
}
</style>
