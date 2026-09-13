<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>{{ isEdit ? '编辑文章' : '新增文章' }}</span>
        <div class="actions">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="80px" class="article-form">
      <el-form-item label="标题">
        <el-input v-model="form.title" placeholder="请输入文章标题" maxlength="200" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="分类">
            <el-tree-select
              v-model="form.categoryId"
              :data="categories"
              :props="catProps"
              check-strictly
              clearable
              placeholder="请选择分类"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="标签">
            <el-select v-model="form.tagIds" multiple filterable clearable placeholder="请选择标签" style="width: 100%">
              <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="封面">
        <el-upload
          :action="'/api/admin/upload/image'"
          :headers="uploadHeaders"
          :show-file-list="false"
          accept="image/*"
          :on-success="onCoverSuccess"
          :on-error="onCoverError"
        >
          <img v-if="form.cover" :src="form.cover" class="cover-preview" alt="封面" />
          <el-button v-else>上传封面</el-button>
        </el-upload>
      </el-form-item>

      <el-form-item label="摘要">
        <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" placeholder="文章摘要（可选）" />
      </el-form-item>

      <el-form-item label="设置">
        <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" active-text="置顶" />
        <el-radio-group v-model="form.status" class="status-group">
          <el-radio :value="1">发布</el-radio>
          <el-radio :value="0">草稿</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="正文">
        <div class="editor-wrap">
          <Toolbar class="editor-toolbar" :editor="editorRef" :default-config="toolbarConfig" mode="default" />
          <Editor
            v-model="form.content"
            class="editor-body"
            :default-config="editorConfig"
            mode="default"
            @onCreated="onEditorCreated"
          />
        </div>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive, shallowRef, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { categoryTree } from '@/api/category'
import { tagList } from '@/api/tag'
import { getArticleForEdit, saveArticle, updateArticle } from '@/api/article'

const route = useRoute()
const router = useRouter()

const articleId = route.params.id
const isEdit = computed(() => !!articleId)

const categories = ref([])
const tags = ref([])
const saving = ref(false)

const form = reactive({
  id: null,
  title: '',
  summary: '',
  content: '',
  cover: '',
  categoryId: null,
  status: 0,
  isTop: 0,
  tagIds: []
})

const catProps = { label: 'name', value: 'id', children: 'children' }
const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }

// 富文本编辑器实例
const editorRef = shallowRef()
const toolbarConfig = {}
const editorConfig = {
  placeholder: '请输入正文...',
  MENU_CONF: {
    uploadImage: {
      server: '/api/admin/upload/image',
      fieldName: 'file',
      headers: { Authorization: localStorage.getItem('token') || '' },
      maxFileSize: 5 * 1024 * 1024,
      customInsert(res, insertFn) {
        if (res && res.code === 200 && res.data) {
          insertFn(res.data, '', '')
        } else {
          ElMessage.error((res && res.msg) || '图片上传失败')
        }
      }
    }
  }
}

function onEditorCreated(editor) {
  editorRef.value = editor
}

function onCoverSuccess(res) {
  if (res && res.code === 200 && res.data) {
    form.cover = res.data
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.error((res && res.msg) || '封面上传失败')
  }
}

function onCoverError() {
  ElMessage.error('封面上传失败')
}

async function loadOptions() {
  const [c, t] = await Promise.all([categoryTree(), tagList()])
  categories.value = c
  tags.value = t
}

async function loadArticle() {
  if (!articleId) {
    return
  }
  const data = await getArticleForEdit(articleId)
  form.id = data.id
  form.title = data.title
  form.summary = data.summary || ''
  form.content = data.content || ''
  form.cover = data.cover || ''
  form.categoryId = data.categoryId || null
  form.status = data.status === undefined ? 0 : data.status
  form.isTop = data.isTop === undefined ? 0 : data.isTop
  form.tagIds = data.tagIds || []
}

async function onSubmit() {
  if (!form.title || !form.title.trim()) {
    ElMessage.warning('请输入文章标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      id: form.id,
      title: form.title.trim(),
      summary: form.summary,
      content: form.content,
      cover: form.cover,
      categoryId: form.categoryId || null,
      status: form.status,
      isTop: form.isTop,
      tagIds: form.tagIds || []
    }
    if (isEdit.value) {
      await updateArticle(payload)
    } else {
      await saveArticle(payload)
    }
    ElMessage.success('保存成功')
    router.push('/admin/articles')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/admin/articles')
}

onMounted(() => {
  loadOptions()
  loadArticle()
})

onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor) {
    editor.destroy()
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.actions {
  display: flex;
  gap: 8px;
}
.cover-preview {
  height: 80px;
  border-radius: 4px;
}
.status-group {
  margin-left: 20px;
}
.editor-wrap {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.editor-toolbar {
  border-bottom: 1px solid #dcdfe6;
}
.editor-body {
  height: 420px;
  overflow-y: hidden;
}
@media (max-width: 768px) {
  .status-group {
    margin-left: 0;
    margin-top: 8px;
  }
}
</style>
