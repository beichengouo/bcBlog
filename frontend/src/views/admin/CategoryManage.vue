<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>分类管理</span>
        <el-button type="primary" @click="openAdd(null)">新增顶级分类</el-button>
      </div>
    </template>

    <el-table :data="tree" row-key="id" :tree-props="{ children: 'children' }" default-expand-all v-loading="loading">
      <el-table-column prop="name" label="分类名称" min-width="180" />
      <el-table-column prop="sort" label="排序" width="100" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAdd(row)">新增子级</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑分类' : '新增分类'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="父级分类">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="不选则为顶级分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryTree, saveCategory, updateCategory, deleteCategory } from '@/api/category'

const tree = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, name: '', parentId: null, sort: 0 })

// 父级选择器的数据：加一个“顶级分类”虚拟根节点
const parentOptions = computed(() => [{ id: 0, name: '顶级分类', children: tree.value }])

async function load() {
  loading.value = true
  try {
    tree.value = await categoryTree()
  } finally {
    loading.value = false
  }
}

function openAdd(parent) {
  form.id = null
  form.name = ''
  form.parentId = parent ? parent.id : 0
  form.sort = 0
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name
  form.parentId = row.parentId || 0
  form.sort = row.sort || 0
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  const payload = {
    id: form.id,
    name: form.name.trim(),
    parentId: form.parentId || 0,
    sort: form.sort || 0
  }
  if (form.id) {
    await updateCategory(payload)
  } else {
    await saveCategory(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteCategory(row.id)
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
</style>
