<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>API 调用审计</span>
        <div class="toolbar-right">
          <el-input v-model="keyword" placeholder="按动作搜索，如 沙盒" style="width: 180px" clearable @change="reload" />
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 250px"
            @change="reload"
          />
          <el-button @click="reload">刷新</el-button>
        </div>
      </div>
    </template>

    <el-alert
      class="tips"
      type="info"
      :closable="false"
      title="记录谁在什么时候调用了哪个会消耗额度或涉及密钥的功能；保留天数在「系统设置 → 数据清理」里配置（默认 3 天）。"
    />

    <el-table :data="summary" v-loading="loadingSummary" class="mt" size="small">
      <el-table-column prop="adminName" label="管理员" min-width="160" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'SUPER' ? 'danger' : row.role === 'SYSTEM' ? 'info' : 'primary'">
            {{ row.role === 'SUPER' ? '超级管理员' : row.role === 'SYSTEM' ? '定时任务' : row.role === 'ADMIN1' ? '一级' : '二级' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="today" label="今天调用" width="110" />
      <el-table-column prop="week" label="近 7 天" width="110" />
    </el-table>

    <el-table :data="list" v-loading="loading" class="mt">
      <el-table-column prop="createTime" label="时间" width="165" />
      <el-table-column prop="adminName" label="调用者" width="130">
        <template #default="{ row }">{{ row.adminName || '定时任务' }}</template>
      </el-table-column>
      <el-table-column prop="action" label="动作" min-width="170" />
      <el-table-column label="来源" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.source === 'schedule' ? 'info' : 'primary'">
            {{ row.source === 'schedule' ? '定时' : '手动' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="target" label="使用的服务商 / 接口" min-width="200" show-overflow-tooltip />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">
            {{ row.success === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costMs" label="耗时" width="90">
        <template #default="{ row }">{{ row.costMs ? row.costMs + ' ms' : '—' }}</template>
      </el-table-column>
      <!-- 输出字数：配合上面的耗时，能看出放开篇幅后有没有变慢 / 撞到接口隐形上限 -->
      <el-table-column prop="outputChars" label="输出字数" width="100">
        <template #default="{ row }">{{ row.outputChars == null ? '—' : row.outputChars + ' 字' }}</template>
      </el-table-column>
      <el-table-column prop="message" label="失败原因" min-width="180" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="130" />
    </el-table>
    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="onPageChange"
    />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { auditList, auditSummary } from '@/api/audit'

const list = ref([])
const summary = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const keyword = ref('')
const range = ref([])
const loading = ref(false)
const loadingSummary = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await auditList({
      keyword: keyword.value || undefined,
      startDate: range.value && range.value[0] ? range.value[0] : undefined,
      endDate: range.value && range.value[1] ? range.value[1] : undefined,
      page: page.value,
      size: pageSize
    })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  loadingSummary.value = true
  try {
    summary.value = await auditSummary()
  } finally {
    loadingSummary.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function onPageChange(value) {
  page.value = value
  load()
}

onMounted(async () => {
  await Promise.all([load(), loadSummary()])
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.tips { margin-bottom: 12px; }
.mt { margin-top: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
