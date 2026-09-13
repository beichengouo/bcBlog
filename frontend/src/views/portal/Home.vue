<template>
  <div class="home">
    <aside class="sidebar">
      <el-card class="side-card">
        <template #header>分类</template>
        <el-tree
          :data="categories"
          :props="catProps"
          node-key="id"
          :default-expand-all="true"
          :current-node-key="currentCategory"
          highlight-current
          @node-click="onCategoryClick"
        >
          <template #default="{ data }">
            <span>{{ data.name }}</span>
          </template>
        </el-tree>
        <div v-if="currentCategory" class="clear-filter" @click="clearFilter">清除筛选</div>
      </el-card>

      <el-card class="side-card">
        <template #header>标签</template>
        <div class="tag-cloud">
          <el-tag
            v-for="t in tags"
            :key="t.id"
            :type="currentTag === t.id ? 'primary' : 'info'"
            class="tag-item"
            @click="onTagClick(t)"
          >{{ t.name }}</el-tag>
        </div>
      </el-card>
    </aside>

    <section class="content">
      <ArticleList :params="listParams" />
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { portalCategoryTree } from '@/api/category'
import { portalTagList } from '@/api/tag'
import ArticleList from '@/components/portal/ArticleList.vue'

const route = useRoute()
const router = useRouter()

const categories = ref([])
const tags = ref([])
const catProps = { label: 'name', children: 'children' }

const currentCategory = computed(() => (route.query.category ? Number(route.query.category) : null))
const currentTag = computed(() => (route.query.tag ? Number(route.query.tag) : null))
const currentKeyword = computed(() => route.query.keyword || '')

// 根据地址栏筛选条件生成列表请求参数
const listParams = computed(() => {
  const params = {}
  if (currentCategory.value) params.categoryId = currentCategory.value
  if (currentTag.value) params.tagId = currentTag.value
  if (currentKeyword.value) params.keyword = currentKeyword.value
  return params
})

function onCategoryClick(node) {
  router.push({ path: '/portal', query: { category: node.id } })
}

function onTagClick(tag) {
  router.push({ path: '/portal', query: { tag: tag.id } })
}

function clearFilter() {
  router.push({ path: '/portal' })
}

onMounted(async () => {
  categories.value = await portalCategoryTree()
  tags.value = await portalTagList()
})
</script>

<style scoped>
.home {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.sidebar {
  width: 240px;
  flex-shrink: 0;
}
.side-card {
  margin-bottom: 16px;
}
.content {
  flex: 1;
  min-width: 0;
}
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag-item {
  cursor: pointer;
}
.clear-filter {
  margin-top: 10px;
  color: #409eff;
  cursor: pointer;
  font-size: 13px;
}
@media (max-width: 768px) {
  .home {
    flex-direction: column;
  }
  .sidebar {
    width: 100%;
  }
}
</style>
