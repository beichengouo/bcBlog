<template>
  <div class="report-manage">
    <el-card>
      <template #header>
        <div class="toolbar">
          <div class="toolbar-left">
            <span class="title">举报管理</span>
            <el-tag v-if="pendingCount > 0" type="danger" size="small" effect="dark">
              待处理 {{ pendingCount }}
            </el-tag>
          </div>
          <div class="toolbar-right">
            <el-radio-group v-model="status" size="small" @change="onFilter">
              <el-radio-button value="pending">待处理</el-radio-button>
              <el-radio-button value="handled">已处理</el-radio-button>
              <el-radio-button value="ignored">已忽略</el-radio-button>
              <el-radio-button value="">全部</el-radio-button>
            </el-radio-group>
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="tip">
        用户举报的违法有害信息会进入这里。举报时已保存<b>被举报内容快照</b>，即使原评论被删除也能追溯；
        处理结论、处理人、处理时间会一并留档，供监管核查。
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="createTime" label="举报时间" width="160" />
        <el-table-column label="举报内容" min-width="300">
          <template #default="{ row }">
            <div class="snapshot">{{ row.contentSnapshot || '（内容已被删除，见快照说明）' }}</div>
            <div class="muted" v-if="row.articleTitle">评论于《{{ row.articleTitle }}》</div>
            <div class="muted" v-if="row.detail">补充：{{ row.detail }}</div>
          </template>
        </el-table-column>
        <el-table-column label="原因" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.reason }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reporterName" label="举报人" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理记录" width="220">
          <template #default="{ row }">
            <template v-if="row.handleTime">
              <div>{{ row.handleNote || '（未填写结论）' }}</div>
              <div class="muted">{{ row.handlerName || '管理员' }} · {{ row.handleTime }}</div>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openHandle(row, 'handled')">处理</el-button>
            <el-button size="small" @click="openHandle(row, 'ignored')">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > size"
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="handleForm.status === 'ignored' ? '忽略举报' : '处理举报'" width="min(92vw, 520px)">
      <el-form label-width="90px">
        <el-form-item label="被举报内容">
          <div class="snapshot">{{ current?.contentSnapshot }}</div>
        </el-form-item>
        <el-form-item label="处理方式">
          <el-radio-group v-model="handleForm.status">
            <el-radio value="handled">已处理（属实，已处置内容）</el-radio>
            <el-radio value="ignored">已忽略（不属实）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处置方式">
          <el-radio-group v-model="handleForm.commentAction">
            <el-radio value="reject">拒绝该评论（前台不再展示）</el-radio>
            <el-radio value="delete">删除该评论</el-radio>
            <el-radio value="none">仅记录结论</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理结论">
          <el-input
            v-model="handleForm.note"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="例如：属实，已删除该评论并提醒用户；或不属实，内容正常"
          />
        </el-form-item>
      </el-form>
      <div class="tip">
        提示：选择「拒绝 / 删除」会在记录结论的同时把评论从前台撤下；选「仅记录结论」则只留档案，之后可到「评论管理」手动处置。
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminReportPage, handleReport } from '@/api/report'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('pending')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const current = ref(null)
const pendingCount = ref(0)
// commentAction：reject 拒绝（默认）/ delete 删除 / none 仅记录
const handleForm = reactive({ status: 'handled', note: '', commentAction: 'reject' })

async function load() {
  loading.value = true
  try {
    const data = await adminReportPage({ status: status.value || undefined, page: page.value, size })
    list.value = data.list || []
    total.value = data.total || 0
    if (status.value === 'pending') {
      pendingCount.value = total.value
    } else {
      const pending = await adminReportPage({ status: 'pending', page: 1, size: 1 })
      pendingCount.value = pending.total || 0
    }
  } finally {
    loading.value = false
  }
}

function onFilter() {
  page.value = 1
  load()
}

function statusText(value) {
  if (value === 'handled') return '已处理'
  if (value === 'ignored') return '已忽略'
  return '待处理'
}

function statusType(value) {
  if (value === 'handled') return 'success'
  if (value === 'ignored') return 'info'
  return 'danger'
}

function openHandle(row, result) {
  current.value = row
  handleForm.status = result
  handleForm.note = ''
  // 属实默认顺手拒绝评论；忽略则不动评论
  handleForm.commentAction = result === 'handled' ? 'reject' : 'none'
  dialogVisible.value = true
}

async function onHandle() {
  if (!current.value) return
  saving.value = true
  try {
    await handleReport(current.value.id, {
      status: handleForm.status,
      note: handleForm.note,
      commentAction: handleForm.status === 'ignored' ? 'none' : handleForm.commentAction
    })
    ElMessage.success('已记录处理结果')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title {
  font-weight: 700;
}
.tip {
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}
.snapshot {
  color: var(--el-text-color-primary);
  word-break: break-all;
}
.muted {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
