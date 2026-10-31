<template>
  <div class="release-notes" v-html="html"></div>
</template>
<script setup>
import { computed } from 'vue'
import { renderReleaseNotes } from '@/utils/gameReleaseNotes'
const props = defineProps({ text: { type: String, default: '' } })
const html = computed(() => renderReleaseNotes(props.text))
</script>
<style scoped>
.release-notes { overflow-wrap: anywhere; line-height: 1.9; }
.release-notes :deep(> :first-child) { margin-top: 0; }
.release-notes :deep(> :last-child) { margin-bottom: 0; }
.release-notes :deep(p) { margin: .65em 0; }
.release-notes :deep(h1), .release-notes :deep(h2), .release-notes :deep(h3), .release-notes :deep(h4), .release-notes :deep(h5), .release-notes :deep(h6) { font-size: 1.1em; font-weight: 600; margin: 1em 0 .4em; color: inherit; }
.release-notes :deep(ul), .release-notes :deep(ol) { padding-left: 1.6em; margin: .65em 0; }
.release-notes :deep(ul) { list-style: disc; }
.release-notes :deep(ol) { list-style: decimal; }
.release-notes :deep(li + li) { margin-top: .25em; }
.release-notes :deep(a) { color: inherit; text-decoration: underline; text-underline-offset: 3px; }
.release-notes :deep(code) { padding: .1em .35em; background: #8882; border-radius: 4px; font-family: Consolas, monospace; }
.release-notes :deep(pre) { max-width: 100%; overflow-x: auto; padding: 12px; background: #8881; border-radius: 8px; line-height: 1.6; }
.release-notes :deep(pre code) { padding: 0; background: none; }
.release-notes :deep(blockquote) { margin: .8em 0; padding-left: 12px; border-left: 3px solid currentColor; opacity: .8; }
.release-notes :deep(table) { border-collapse: collapse; display: block; max-width: 100%; overflow-x: auto; }
.release-notes :deep(th), .release-notes :deep(td) { padding: 6px 10px; border: 1px solid #8885; }
.release-notes :deep(img) { max-width: 100%; height: auto; border-radius: 6px; }
.release-notes :deep(hr) { border: 0; border-top: 1px solid #8884; margin: 1em 0; }
</style>
