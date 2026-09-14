<template>
  <div class="point-page">
    <el-card>
      <template #header>
        <div class="toolbar">
          <span>积分发放</span>
        </div>
      </template>
      <el-form :model="form" label-width="100px" class="grant-form">
        <el-form-item label="发放对象">
          <el-radio-group v-model="targetType">
            <el-radio value="all">全部普通用户</el-radio>
            <el-radio value="some">指定用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="targetType === 'some'" label="选择用户">
          <el-select v-model="form.userIds" multiple filterable placeholder="选择要发放的用户" style="width: 100%">
            <el-option v-for="u in members" :key="u.id" :label="`${u.nickname || u.username}（${u.email || u.username}）`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="积分数量">
          <el-input-number v-model="form.points" :min="-100000" :max="100000" />
          <span class="tip">正数为发放，负数为扣减</span>
        </el-form-item>
        <el-form-item label="积分来源">
          <el-input v-model="form.type" placeholder="默认 admin" maxlength="50" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.reason" placeholder="默认管理员发放" maxlength="200" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="granting" @click="onGrant">确认发放</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="log-card">
      <template #header>
        <div class="toolbar">
          <span>积分流水</span>
          <el-input v-model="filterUserId" placeholder="按用户 ID 筛选" clearable class="search" @keyup.enter="reload" />
        </div>
      </template>
      <el-table :data="logs" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userId" label="用户 ID" width="90" />
        <el-table-column prop="type" label="来源" width="120" />
        <el-table-column label="积分" width="90">
          <template #default="{ row }">
            <span :class="row.points >= 0 ? 'plus' : 'minus'">{{ row.points >= 0 ? '+' + row.points : row.points }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" width="90" />
        <el-table-column prop="reason" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="loadLogs"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { memberPage } from '@/api/member'
import { grantPoints, pointLogs } from '@/api/point'

const targetType = ref('all')
const members = ref([])
const granting = ref(false)
const form = reactive({ userIds: [], points: 1, type: 'admin', reason: '管理员发放' })

const logs = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filterUserId = ref('')

async function loadMembers() {
  try {
    members.value = await memberPage()
  } catch (e) {
    members.value = []
  }
}

async function onGrant() {
  if (!form.points) {
    ElMessage.warning('请输入积分数量')
    return
  }
  if (targetType.value === 'some' && !form.userIds.length) {
    ElMessage.warning('请选择要发放的用户')
    return
  }
  granting.value = true
  try {
    const payload = {
      userIds: targetType.value === 'all' ? [] : form.userIds,
      points: form.points,
      type: form.type,
      reason: form.reason
    }
    const count = await grantPoints(payload)
    ElMessage.success(`已为 ${count} 个用户发放积分`)
    reload()
  } finally {
    granting.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filterUserId.value) {
      params.userId = filterUserId.value
    }
    const data = await pointLogs(params)
    logs.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  loadLogs()
}

onMounted(() => {
  loadMembers()
  loadLogs()
})
</script>

<style scoped>
.point-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.grant-form {
  max-width: 620px;
}
.tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.search {
  width: 200px;
}
.plus {
  color: #67c23a;
  font-weight: 600;
}
.minus {
  color: #f56c6c;
  font-weight: 600;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
