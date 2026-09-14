<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>等级配置</span>
        <el-button type="primary" @click="openEdit()">新增等级</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="level" label="等级" width="90" />
      <el-table-column prop="name" label="等级名称" min-width="140" />
      <el-table-column prop="expRequired" label="所需经验" width="120" />
      <el-table-column prop="color" label="颜色" width="120">
        <template #default="{ row }">
          <span v-if="row.color" class="color-dot" :style="{ background: row.color }"></span>
          <span>{{ row.color || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑等级' : '新增等级'" width="min(92vw, 420px)">
      <el-form :model="form" label-width="90px">
        <el-form-item label="等级">
          <el-input-number v-model="form.level" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="等级名称">
          <el-input v-model="form.name" placeholder="如：活跃之星" maxlength="50" />
        </el-form-item>
        <el-form-item label="所需经验">
          <el-input-number v-model="form.expRequired" :min="0" :max="99999999" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-input v-model="form.color" placeholder="如 #ff6f9f，可留空" maxlength="20" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { levelList, saveLevel, deleteLevel } from '@/api/member'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, level: 1, name: '', expRequired: 0, color: '' })

async function load() {
  loading.value = true
  try {
    list.value = await levelList()
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  form.id = row ? row.id : null
  form.level = row ? row.level : (list.value.length ? list.value[list.value.length - 1].level + 1 : 1)
  form.name = row ? row.name : ''
  form.expRequired = row ? row.expRequired : 0
  form.color = row ? row.color || '' : ''
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入等级名称')
    return
  }
  saving.value = true
  try {
    await saveLevel({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除等级「${row.name}」吗？`, '删除等级', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteLevel(row.id)
  ElMessage.success('删除成功')
  load()
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
.color-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: -1px;
}
</style>
