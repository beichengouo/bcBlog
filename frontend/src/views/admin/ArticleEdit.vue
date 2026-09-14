<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>{{ isEdit ? '编辑文章' : '新增文章' }}</span>
        <div class="actions">
          <el-button @click="goBack">返回</el-button>
          <el-button type="success" plain @click="openAiDialog">AI 写文章</el-button>
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
        <div class="cover-box">
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
          <el-button :loading="coverLoading" @click="onRandomCover">随机封面</el-button>
          <el-button v-if="form.cover" @click="form.cover = ''">移除</el-button>
        </div>
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
        <div class="editor-actions">
          <EmojiPicker label="插入表情" @select="onEmojiSelect" />
        </div>
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

    <el-dialog v-model="aiVisible" title="AI 一键写文章" width="min(92vw, 560px)">
      <el-form label-width="60px" class="ai-form">
        <el-form-item label="服务商">
          <el-select v-model="aiProviderId" placeholder="选择服务商" style="width: 100%" @change="onAiProviderChange">
            <el-option v-for="p in aiProviders" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="aiModel" filterable placeholder="选择模型" style="width: 100%" :loading="aiModelLoading">
            <el-option v-for="m in aiModels" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-input
        v-model="aiRequirement"
        type="textarea"
        :rows="6"
        placeholder="例如：写一篇 Spring Boot 入门教程，涵盖环境搭建、第一个接口和常见配置"
      />
      <p class="ai-tip">生成后会直接填充到上方表单，请检查后再点“保存”发布。</p>
      <template #footer>
        <el-button @click="aiVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiLoading" @click="onAiGenerate">生成并填充</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, shallowRef, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { categoryTree } from '@/api/category'
import { tagList, saveTag } from '@/api/tag'
import { getArticleForEdit, saveArticle, updateArticle } from '@/api/article'
import { aiProviderList, aiProviderModels, aiGenerateArticle } from '@/api/ai'
import { randomAcgCover } from '@/api/acg'
import EmojiPicker from '@/components/portal/EmojiPicker.vue'

const route = useRoute()
const router = useRouter()

const articleId = route.params.id
const isEdit = computed(() => !!articleId)

const categories = ref([])
const tags = ref([])
const saving = ref(false)
const aiVisible = ref(false)
const aiRequirement = ref('')
const aiLoading = ref(false)
const aiProviders = ref([])
const aiProviderId = ref(null)
const aiModels = ref([])
const aiModel = ref('')
const aiModelLoading = ref(false)
const coverLoading = ref(false)

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

/** 插入表情图片到富文本 */
function onEmojiSelect(emoji) {
  if (editorRef.value) {
    editorRef.value.dangerouslyInsertHtml(`<img src="${emoji.url}" style="width:24px;height:24px;vertical-align:middle" />`)
  }
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

/** 随机获取一张 ACG 图片作为文章封面。 */
async function onRandomCover() {
  coverLoading.value = true
  try {
    form.cover = await randomAcgCover()
    ElMessage.success('已随机选择封面，保存文章后生效')
  } finally {
    coverLoading.value = false
  }
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

// 在分类树中按名称递归查找分类 id
function findCategoryId(nodes, name) {
  if (!Array.isArray(nodes)) return null
  for (const n of nodes) {
    if (n.name === name) return n.id
    const found = findCategoryId(n.children, name)
    if (found) return found
  }
  return null
}

// 把 AI 返回的标签名映射为已有标签 id，缺失时自动创建
async function resolveTagIds(names) {
  const ids = []
  for (const raw of names) {
    const name = String(raw).trim()
    if (!name) continue
    let t = tags.value.find((x) => x.name === name)
    if (!t) {
      await saveTag({ name })
      tags.value = await tagList()
      t = tags.value.find((x) => x.name === name)
    }
    if (t) ids.push(t.id)
  }
  return ids
}

async function onAiGenerate() {
  if (!aiProviderId.value) {
    ElMessage.warning('请选择服务商')
    return
  }
  if (!aiModel.value) {
    ElMessage.warning('请选择模型')
    return
  }
  if (!aiRequirement.value.trim()) {
    ElMessage.warning('请输入文章需求')
    return
  }
  aiLoading.value = true
  try {
    const data = await aiGenerateArticle({
      providerId: aiProviderId.value,
      model: aiModel.value,
      requirement: aiRequirement.value.trim()
    })
    if (data.title) form.title = data.title
    if (data.summary) form.summary = data.summary
    if (data.content) {
      form.content = data.content
      if (editorRef.value) {
        editorRef.value.setHtml(data.content)
      }
    }
    if (data.tags && data.tags.length) {
      form.tagIds = await resolveTagIds(data.tags)
    }
    if (data.category) {
      form.categoryId = findCategoryId(categories.value, data.category) || form.categoryId
    }
    ElMessage.success('已填充到表单，请检查后点击保存发布')
    aiVisible.value = false
    aiRequirement.value = ''
  } finally {
    aiLoading.value = false
  }
}

async function openAiDialog() {
  aiVisible.value = true
  try {
    aiProviders.value = await aiProviderList()
  } catch (e) {
    aiProviders.value = []
  }
  if (!aiProviderId.value && aiProviders.value.length) {
    const def = aiProviders.value.find((p) => p.isDefault === 1) || aiProviders.value[0]
    aiProviderId.value = def.id
    onAiProviderChange(def.id)
  }
}

async function onAiProviderChange(id) {
  aiModel.value = ''
  aiModels.value = []
  if (!id) return
  aiModelLoading.value = true
  try {
    aiModels.value = await aiProviderModels(id)
  } catch (e) {
    aiModels.value = []
  } finally {
    aiModelLoading.value = false
  }
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
.cover-box {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
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
.editor-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.editor-toolbar {
  border-bottom: 1px solid #dcdfe6;
}
.editor-body {
  height: 420px;
  overflow-y: hidden;
}
.ai-tip {
  margin: 10px 0 0;
  color: #909399;
  font-size: 13px;
}
@media (max-width: 768px) {
  .status-group {
    margin-left: 0;
    margin-top: 8px;
  }
}
</style>
