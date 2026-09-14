<template>
  <el-popover placement="top" width="320" trigger="click">
    <template #reference>
      <el-button size="small" :type="type">{{ label }}</el-button>
    </template>
    <div class="emoji-panel" v-loading="loading">
      <div v-if="list.length" class="emoji-list">
        <button v-for="e in list" :key="e.id" class="emoji-item" :title="e.name || e.pack" @click="onSelect(e)">
          <img :src="e.url" alt="" />
        </button>
      </div>
      <el-empty v-else-if="!loading" description="暂无表情" :image-size="50" />
    </div>
  </el-popover>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { portalEmojiList } from '@/api/emoji'

defineProps({
  label: { type: String, default: '表情' },
  type: { type: String, default: '' }
})

const emit = defineEmits(['select'])
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await portalEmojiList()
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onSelect(emoji) {
  emit('select', emoji)
}

onMounted(load)
</script>

<style scoped>
.emoji-panel {
  max-height: 260px;
  overflow-y: auto;
}
.emoji-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(48px, 1fr));
  gap: 8px;
}
.emoji-item {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--glass-bg);
  padding: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease, border-color 0.2s ease;
}
.emoji-item:hover {
  transform: scale(1.08);
  border-color: var(--accent);
}
.emoji-item img {
  width: 36px;
  height: 36px;
  object-fit: contain;
  display: block;
}
</style>
