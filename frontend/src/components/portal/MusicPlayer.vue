<template>
  <div class="music glass" :class="{ open }">
    <button class="fab" @click="open = !open">
      <span v-if="playing" class="eq"><i></i><i></i><i></i></span>
      <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M9 18V5l12-2v13" />
        <circle cx="6" cy="18" r="3" />
        <circle cx="18" cy="16" r="3" />
      </svg>
    </button>

    <transition name="pop">
      <div v-if="open" class="panel">
        <div class="head">
          <span class="label">音乐</span>
          <button class="close" @click="open = false">×</button>
        </div>

        <div class="now">
          <div
            class="disc"
            :class="{ spin: playing }"
            :style="current.pic ? { backgroundImage: `url(${current.pic})` } : {}"
          ></div>
          <div class="meta">
            <div class="title">{{ current.title || '未在播放' }}</div>
            <div class="artist">{{ current.artist || '—' }}</div>
          </div>
        </div>

        <div v-if="currentLyric" class="lyric-line">{{ currentLyric }}</div>

        <div class="progress" @click="seek">
          <div class="bar"><div class="fill" :style="{ width: progress + '%' }"></div></div>
          <div class="times">
            <span>{{ fmt(currentTime) }}</span>
            <span>{{ fmt(duration) }}</span>
          </div>
        </div>

        <div class="controls">
          <button @click="prev"><svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z" /></svg></button>
          <button class="play" @click="toggle">
            <svg v-if="!playing" viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
            <svg v-else viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M6 5h4v14H6zm8 0h4v14h-4z" /></svg>
          </button>
          <button @click="next"><svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M16 6h2v12h-2zM6 18l8.5-6L6 6z" /></svg></button>
        </div>

        <div class="volume">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 5 6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" /></svg>
          <input v-model="volume" type="range" min="0" max="1" step="0.01" @input="setVolume(volume)" />
        </div>

        <div v-if="loading" class="loading">歌单加载中...</div>
        <ul v-else class="list">
          <li
            v-for="(s, i) in songs"
            :key="s.src"
            :class="{ active: i === index }"
            @click="play(i)"
          >
            <span class="name">{{ s.title }}</span>
            <span class="artist">{{ s.artist }}</span>
          </li>
          <li v-if="!songs.length" class="empty">暂无歌曲</li>
        </ul>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useMusicStore } from '@/store/music'

const open = ref(false)
const music = useMusicStore()
const { songs, index, playing, currentTime, duration, volume, loading, current, progress, currentLyric } = storeToRefs(music)
const { play, toggle, next, prev, setVolume } = music

function fmt(s) {
  if (!s || !isFinite(s)) return '00:00'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

function seek(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  music.seekRatio((e.clientX - rect.left) / rect.width)
}

onMounted(() => {
  music.init()
})
</script>

<style scoped>
.music {
  position: fixed;
  left: 18px;
  bottom: 18px;
  z-index: 60;
  border-radius: 22px;
  box-shadow: var(--shadow);
}
.fab {
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--accent-2), var(--accent));
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow);
}
.eq {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 16px;
}
.eq i {
  width: 3px;
  background: #fff;
  border-radius: 2px;
  animation: bounce 0.8s ease-in-out infinite;
}
.eq i:nth-child(1) { height: 10px; }
.eq i:nth-child(2) { height: 16px; animation-delay: 0.15s; }
.eq i:nth-child(3) { height: 8px; animation-delay: 0.3s; }
@keyframes bounce {
  0%, 100% { transform: scaleY(0.4); }
  50% { transform: scaleY(1); }
}
.panel {
  width: 300px;
  padding: 14px 16px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.label {
  font-size: 13px;
  color: var(--text-muted);
  letter-spacing: 1px;
}
.close {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 20px;
  cursor: pointer;
}
.now {
  display: flex;
  align-items: center;
  gap: 12px;
}
.disc {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, #ffd3e2, var(--accent) 60%, var(--accent-2));
  background-size: cover;
  background-position: center;
  box-shadow: var(--shadow);
  flex-shrink: 0;
}
.disc.spin {
  animation: rotate 8s linear infinite;
}
@keyframes rotate {
  to { transform: rotate(360deg); }
}
.meta {
  min-width: 0;
}
.title {
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.artist {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}
.lyric-line {
  margin-top: 10px;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.progress {
  margin: 14px 0 10px;
}
.bar {
  height: 5px;
  background: var(--accent-soft);
  border-radius: 4px;
  cursor: pointer;
}
.fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
  border-radius: 4px;
}
.times {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}
.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-bottom: 12px;
}
.controls button {
  border: none;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  display: flex;
}
.controls .play {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  color: #fff;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow);
}
.volume {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  margin-bottom: 12px;
}
.volume input {
  flex: 1;
  accent-color: var(--accent);
}
.loading,
.empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 12px 0;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 180px;
  overflow-y: auto;
}
.list li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.list li:hover {
  background: var(--accent-soft);
}
.list li.active {
  background: var(--accent-soft);
  color: var(--accent);
}
.list .name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.list .artist {
  color: var(--text-muted);
  flex-shrink: 0;
}
.pop-enter-active,
.pop-leave-active {
  transition: all 0.22s ease;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.96);
}
@media (max-width: 768px) {
  .music {
    left: 12px;
    bottom: 12px;
  }
  .panel {
    width: 260px;
  }
}
</style>
