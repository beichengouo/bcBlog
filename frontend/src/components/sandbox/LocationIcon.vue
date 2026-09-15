<template>
  <img v-if="resolved.type === 'image'" :src="resolved.url" :style="imgStyle" alt="" />
  <svg
    v-else
    viewBox="0 0 24 24"
    :width="size"
    :height="size"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path v-for="(d, i) in resolved.icon.paths" :key="i" :d="d" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'
import { resolveSandboxIcon } from '@/config/sandboxIcons'

const props = defineProps({
  icon: { type: String, default: '' },
  size: { type: Number, default: 14 },
  strokeWidth: { type: Number, default: 1.8 }
})

const resolved = computed(() => resolveSandboxIcon(props.icon))
const imgStyle = computed(() => ({
  width: props.size + 'px',
  height: props.size + 'px',
  objectFit: 'contain'
}))
</script>
