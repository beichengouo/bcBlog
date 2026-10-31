<template>
  <div class="release-manager" v-loading="loading">
    <GameCodexImport />
    <el-alert title="支持3 GiB分片续传。新平台首次上传为草稿；更新已发布平台会自动替换线上版本。校验成功后删除旧包，失败则保留旧版。" type="info" :closable="false" show-icon />
    <el-card v-for="platform in platforms" :key="platform.id">
      <template #header><div class="release-card-head"><div><strong>{{ platform.name }}</strong><span>{{ platform.hint }}</span></div><el-tag :type="packages[platform.id]?.published ? 'success' : 'info'">{{ packages[platform.id]?.published ? '已发布' : '未发布' }}</el-tag></div></template>
      <div v-if="packages[platform.id]" class="current-package"><div><strong>{{ packages[platform.id].version }}</strong> · {{ formatPackageSize(packages[platform.id].size) }}</div><p>{{ packages[platform.id].fileName }}</p><p class="hash">SHA-256：{{ packages[platform.id].sha256 }}</p><el-button :disabled="!!busyPlatform || readingNotes" :type="packages[platform.id].published ? 'warning' : 'primary'" @click="setPublished(platform.id)">{{ packages[platform.id].published ? '下架下载' : '发布下载' }}</el-button></div>
      <el-form label-position="top" class="package-form">
        <el-form-item label="安装包版本"><el-input v-model="forms[platform.id].version" maxlength="40" placeholder="例如 V0.55" :disabled="busyPlatform === platform.id" /></el-form-item>
        <el-form-item label="更新说明">
          <div class="notes-editor">
            <div class="notes-toolbar">
              <label class="notes-import" :class="{ 'is-disabled': !!busyPlatform || importingNotes[platform.id] }">
                <input class="file-input" type="file" accept=".md,.markdown" :aria-label="`导入${platform.name}的 Markdown 更新说明`" :disabled="!!busyPlatform || importingNotes[platform.id]" @change="importNotes(platform.id, $event)" />
                {{ importingNotes[platform.id] ? '正在读取…' : '导入 Markdown 文件' }}
              </label>
              <span>UTF-8 · .md / .markdown · 最多4000字</span>
            </div>
            <el-input v-model="forms[platform.id].notes" type="textarea" :rows="5" :maxlength="RELEASE_NOTES_MAX_LENGTH" show-word-limit placeholder="支持 Markdown：标题、列表、加粗、链接和代码块。也可直接导入 MD 文件。" :disabled="busyPlatform === platform.id || importingNotes[platform.id]" />
            <p class="field-hint">导入会替换下方内容，导入后可继续编辑；随安装包一起上传保存。</p>
            <details v-if="forms[platform.id].notes" class="notes-preview"><summary>预览更新说明</summary><ReleaseNotes :text="forms[platform.id].notes" /></details>
          </div>
        </el-form-item>
        <el-form-item label="选择安装包">
          <label class="package-dropzone" :class="{ 'is-dragging': dragDepth[platform.id] > 0, 'is-disabled': !!busyPlatform, 'has-file': !!forms[platform.id].file }"
            @dragenter.prevent="enterPackageDrag(platform.id, $event)" @dragover.prevent="overPackageDrag($event)" @dragleave.prevent="leavePackageDrag(platform.id)" @drop.prevent="dropPackage(platform.id, $event)">
            <input class="file-input" type="file" :accept="platform.accept" :aria-label="`选择${platform.name}安装包`" :disabled="!!busyPlatform" @change="selectFile(platform.id, $event)" />
            <span class="dropzone-icon" aria-hidden="true">↑</span>
            <strong>{{ forms[platform.id].file ? forms[platform.id].file.name : '拖拽安装包到这里，或点击选择' }}</strong>
            <span>{{ forms[platform.id].file ? `${formatPackageSize(forms[platform.id].file.size)} · 拖入或点击可重新选择` : `${platform.hint} · 单个文件不超过3 GiB` }}</span>
            <small v-if="busyPlatform">上传期间暂不可更换文件</small>
          </label>
        </el-form-item>
        <div v-if="progressPlatform === platform.id" role="status" class="upload-status"><el-progress :percentage="progress" :stroke-width="8" /><p>{{ phase }}</p><p v-if="busyPlatform === platform.id && !merging">{{ transferred }} / {{ formatPackageSize(forms[platform.id].file?.size || 0) }} · {{ speed }}</p></div>
        <div class="upload-actions"><el-button type="primary" :disabled="!!busyPlatform || readingNotes || !forms[platform.id].file || !forms[platform.id].version.trim()" @click="upload(platform.id)">上传 / 继续上传</el-button><el-button v-if="busyPlatform === platform.id && !merging" @click="pauseUpload">暂停上传</el-button></div>
      </el-form>
      <p class="release-tip">暂停或刷新后重新选择同一文件可续传，24小时未活动的分片自动清理。合并时需要约“新包大小×2＋旧包大小”的峰值空间；新版完成后自动回收旧包。</p>
    </el-card>
    <router-link to="/portal/games/pixelrogue#game-release" target="_blank">查看前台下载区 ↗</router-link>
  </div>
</template>
<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGamePackages, publishGamePackage, startGameUpload, gameUploadStatus, uploadGameChunk, completeGameUpload } from '@/api/gamePackages'
import { formatPackageSize, validateGamePackageFile } from '@/utils/gamePackages'
import { readReleaseNotesFile, RELEASE_NOTES_MAX_LENGTH } from '@/utils/gameReleaseNotes'
import ReleaseNotes from '@/components/ReleaseNotes.vue'
import GameCodexImport from '@/components/GameCodexImport.vue'
const platforms = [{ id: 'android', name: 'Android 安卓版', hint: 'APK 安装包', accept: '.apk' }, { id: 'pc', name: 'Windows PC版', hint: 'EXE 安装程序 / ZIP 压缩包', accept: '.exe,.zip' }]
const forms = reactive({ android: { version: '', notes: '', file: null }, pc: { version: '', notes: '', file: null } })
const packages = ref({})
const loading = ref(false)
const busyPlatform = ref('')
const progress = ref(0)
const phase = ref('')
const merging = ref(false)
const progressPlatform = ref('')
const transferred = ref('0 MiB')
const speed = ref('计算上传速度…')
const dragDepth = reactive({ android: 0, pc: 0 })
const importingNotes = reactive({ android: false, pc: false })
const readingNotes = computed(() => Object.values(importingNotes).some(Boolean))
let controller
let paused = false
let disposed = false
function selectFile(platform, event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) acceptPackage(platform, file)
}
function acceptPackage(platform, file) {
  if (busyPlatform.value) return
  const error = validateGamePackageFile(platform, file)
  if (error) { ElMessage.error(error); return }
  forms[platform].file = file
  if (progressPlatform.value === platform) { progressPlatform.value = ''; progress.value = 0; phase.value = '' }
}
function enterPackageDrag(platform, event) {
  if (!busyPlatform.value && Array.from(event.dataTransfer?.types || []).includes('Files')) dragDepth[platform]++
}
function overPackageDrag(event) {
  if (event.dataTransfer) event.dataTransfer.dropEffect = busyPlatform.value ? 'none' : 'copy'
}
function leavePackageDrag(platform) { dragDepth[platform] = Math.max(0, dragDepth[platform] - 1) }
function dropPackage(platform, event) {
  dragDepth[platform] = 0
  if (busyPlatform.value) return
  const files = event.dataTransfer?.files
  if (!files?.length) return
  if (files.length !== 1) { ElMessage.error('每次请只拖入一个安装包'); return }
  acceptPackage(platform, files[0])
}
async function importNotes(platform, event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || busyPlatform.value || importingNotes[platform]) return
  importingNotes[platform] = true
  try {
    const notes = await readReleaseNotesFile(file)
    if (!disposed) { forms[platform].notes = notes; ElMessage.success('Markdown 更新说明已导入，可继续编辑') }
  } catch (error) { if (!disposed) ElMessage.error(error.message) }
  finally { importingNotes[platform] = false }
}
async function load() {
  loading.value = true
  try { const data = await adminGamePackages(); if (!disposed) packages.value = Object.fromEntries(data.map((item) => [item.platform, item])) } catch { /* Request interceptor shows the error. */ }
  finally { loading.value = false }
}
async function setPublished(platform) {
  if (busyPlatform.value || readingNotes.value) return
  busyPlatform.value = platform
  try { packages.value[platform] = await publishGamePackage(platform, !packages.value[platform].published); ElMessage.success(packages.value[platform].published ? '下载已发布' : '下载已下架') } catch { /* Request interceptor shows the error. */ }
  finally { busyPlatform.value = '' }
}
function pauseUpload() { paused = true; controller?.abort() }
async function upload(platform) {
  const form = forms[platform], file = form.file
  if (!file || busyPlatform.value || readingNotes.value) return
  const key = `bcblog-game-upload-${platform}`
  const fingerprint = JSON.stringify([file.name, file.size, file.lastModified, form.version.trim(), form.notes])
  busyPlatform.value = platform; progressPlatform.value = platform; progress.value = 0; paused = false; controller = new AbortController()
  try {
    let session
    try {
      const saved = JSON.parse(localStorage.getItem(key) || 'null')
      if (saved?.fingerprint === fingerprint) session = await gameUploadStatus(saved.id)
    } catch { /* Missing sessions restart; already accepted chunks are never overwritten in another session. */ }
    if (paused || disposed) return
    if (!session) {
      session = await startGameUpload({ platform, fileName: file.name, size: file.size, version: form.version.trim(), notes: form.notes })
      try { localStorage.setItem(key, JSON.stringify({ fingerprint, id: session.id })) } catch { /* Continue without persistent resume. */ }
    }
    const done = new Set(session.uploaded)
    let sent = [...done].reduce((sum, index) => sum + Math.min(session.chunkSize, file.size - index * session.chunkSize), 0)
    const initialSent = sent, startedAt = performance.now()
    progress.value = Math.min(99, Math.floor(sent / file.size * 100))
    transferred.value = formatPackageSize(sent)
    const count = Math.ceil(file.size / session.chunkSize)
    for (let index = 0; index < count && !session.result; index++) {
      if (paused || disposed) return
      if (done.has(index)) continue
      phase.value = `上传分片 ${index + 1} / ${count}，可随时暂停`
      const chunk = file.slice(index * session.chunkSize, (index + 1) * session.chunkSize)
      await uploadGameChunk(session.id, index, chunk, (loaded) => {
        const total = sent + Math.min(loaded, chunk.size)
        progress.value = Math.min(99, Math.floor(total / file.size * 100))
        transferred.value = formatPackageSize(total)
        speed.value = `${formatPackageSize((total - initialSent) / Math.max(.1, (performance.now() - startedAt) / 1000))}/s`
      }, controller.signal)
      sent += chunk.size
    }
    if (paused || disposed) return
    merging.value = true; progress.value = 99; phase.value = '正在合并与校验，请勿关闭页面…'
    const result = session.result || await completeGameUpload(session.id)
    packages.value[platform] = result; progress.value = 100; phase.value = result.published ? '新版已替换并发布，旧包已自动清理。' : '上传完成，点击“发布下载”后对访客可见。'
    try { localStorage.removeItem(key) } catch { /* Ignore unavailable storage. */ }
    ElMessage.success(result.published ? '新版安装包已替换发布' : '安装包已保存为未发布草稿')
  } catch (error) { if (!paused && !disposed) phase.value = '上传中断，重新选择同一文件并点击继续上传。' }
  finally { if (paused && !disposed) phase.value = '已暂停，点击继续上传可从已完成的分片恢复。'; merging.value = false; busyPlatform.value = '' }
}
onMounted(load)
onUnmounted(() => { disposed = true; pauseUpload() })
</script>
<style scoped>
.release-manager { display: grid; gap: 24px; }
.release-card-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.release-card-head strong { display: block; font-size: 16px; }
.release-card-head span:not(.el-tag) { display: block; color: var(--admin-muted); font-size: 11px; margin-top: 6px; }
.current-package { padding: 18px; background: #f7f5fb; border-radius: 12px; margin-bottom: 22px; font-size: 13px; }
.current-package p { overflow-wrap: anywhere; color: var(--admin-muted); line-height: 1.8; font-size: 12px; }
.current-package .hash { font-family: Consolas, monospace; font-size: 11px; }
.package-form { max-width: 820px; }
.notes-editor { width: 100%; }
.notes-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 10px; }
.notes-toolbar > span, .field-hint { color: var(--admin-muted); font-size: 11px; line-height: 1.8; }
.field-hint { margin: 7px 0 0; }
.notes-import { position: relative; display: inline-flex; align-items: center; min-height: 34px; padding: 0 12px; border: 1px solid var(--admin-line); border-radius: 8px; color: var(--admin-primary); cursor: pointer; font-size: 12px; }
.notes-import:hover { background: #f6f3fc; }
.file-input { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.notes-import:focus-within, .package-dropzone:focus-within { outline: 2px solid var(--admin-primary); outline-offset: 3px; }
.notes-preview { margin-top: 12px; padding: 12px 14px; border: 1px solid var(--admin-line); border-radius: 10px; font-size: 12px; color: var(--admin-ink); }
.notes-preview summary { color: var(--admin-primary); cursor: pointer; }
.notes-preview .release-notes { margin-top: 12px; max-height: 260px; overflow-y: auto; }
.package-dropzone { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; width: 100%; min-width: 0; gap: 6px; min-height: 160px; padding: 22px 16px; border: 1px dashed #b8aecf; border-radius: 12px; background: #faf9fd; text-align: center; cursor: pointer; transition: border-color .2s, background .2s; }
.package-dropzone:hover, .package-dropzone.is-dragging { border-color: var(--admin-primary); background: #f0ebfa; }
.package-dropzone.is-dragging { outline: 2px solid #7660b526; outline-offset: 3px; }
.dropzone-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 10px; background: #ede7f7; color: var(--admin-primary); font-size: 24px; line-height: 1; margin-bottom: 4px; }
.package-dropzone strong { max-width: 100%; overflow-wrap: anywhere; font-size: 13px; font-weight: 500; line-height: 1.7; color: var(--admin-ink); }
.package-dropzone > span:not(.dropzone-icon), .package-dropzone small { font-size: 11px; color: var(--admin-muted); line-height: 1.8; }
.is-disabled, .package-dropzone.is-disabled:hover { cursor: not-allowed; opacity: .6; background: #f7f8fa; }
.release-tip { font-size: 11px; line-height: 1.9; color: var(--admin-muted); margin-top: 20px; }
.upload-actions { display: flex; gap: 12px; }
.upload-status p { font-size: 12px; color: var(--admin-muted); }
@media (prefers-reduced-motion: reduce) { .package-dropzone { transition: none; } }
</style>
