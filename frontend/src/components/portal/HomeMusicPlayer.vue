<template>
  <div class="home-music">
    <div class="player-card glass">
      <div
        class="cover"
        :class="{ spin: playing }"
        :style="current.pic ? { backgroundImage: `url(${current.pic})` } : {}"
      >
        <span class="cover-hole"></span>
      </div>

      <div class="info">
        <span class="tag">NOW PLAYING</span>
        <h3 class="song-title">{{ current.title || '暂无歌曲' }}</h3>
        <p class="song-artist">{{ current.artist || '—' }}</p>
      </div>

      <div class="progress" @click="seek">
        <div class="bar"><div class="fill" :style="{ width: progress + '%' }"></div></div>
        <div class="times">
          <span>{{ fmt(currentTime) }}</span>
          <span>{{ fmt(duration) }}</span>
        </div>
      </div>

      <div class="controls">
        <button @click="prev" aria-label="上一首">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z" /></svg>
        </button>
        <button class="play" @click="toggle" aria-label="播放/暂停">
          <svg v-if="!playing" viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
          <svg v-else viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M6 5h4v14H6zm8 0h4v14h-4z" /></svg>
        </button>
        <button @click="next" aria-label="下一首">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M16 6h2v12h-2zM6 18l8.5-6L6 6z" /></svg>
        </button>
      </div>
    </div>

    <!-- 歌词栏 -->
    <div class="lyric-bar glass">
      <span class="wave" :class="{ active: playing }">
        <i></i><i></i><i></i><i></i><i></i>
      </span>
      <p class="lyric-text">
        {{ displayed || (current.title ? '♪ ' + current.title : '暂无歌词') }}
        <span class="caret"></span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useMusicStore } from '@/store/music'

const music = useMusicStore()
const { current, progress, playing, currentTime, duration, currentLyric } = storeToRefs(music)
const { prev, toggle, next } = music

const displayed = ref('')
let typeTimer = 0

/** 歌词打字机效果 */
watch(currentLyric, (text) => {
  clearInterval(typeTimer)
  displayed.value = ''
  if (!text) return
  let i = 0
  typeTimer = setInterval(() => {
    displayed.value = text.slice(0, i)
    i++
    if (i > text.length) {
      clearInterval(typeTimer)
    }
  }, 45)
})

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
.home-music {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}
.player-card {
  position: relative;
  flex: 1;
  min-height: 300px;
  padding: 24px;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  overflow: hidden;
}
.player-card::before {
  content: '';
  position: absolute;
  top: -60px;
  right: -60px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: var(--accent-soft);
  filter: blur(10px);
}
.cover {
  position: relative;
  width: 108px;
  height: 108px;
  border-radius: 50%;
  background: radial-gradient(circle at 32% 32%, #ffd3e2, var(--accent) 62%, var(--accent-2));
  background-size: cover;
  background-position: center;
  box-shadow: var(--shadow);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}
.cover.spin {
  animation: rotate 9s linear infinite;
}
.cover-hole {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--card-solid);
  box-shadow: inset 0 0 4px rgba(0, 0, 0, 0.3);
}
@keyframes rotate {
  to { transform: rotate(360deg); }
}
.info {
  text-align: center;
  z-index: 1;
  max-width: 100%;
}
.tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11px;
  letter-spacing: 1px;
}
.song-title {
  margin: 8px 0 4px;
  font-size: 18px;
  color: var(--text-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.song-artist {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}
.progress {
  width: 100%;
  z-index: 1;
}
.bar {
  height: 5px;
  border-radius: 4px;
  background: var(--accent-soft);
  cursor: pointer;
}
.fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
}
.times {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-muted);
}
.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
  z-index: 1;
}
.controls button {
  border: none;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  display: flex;
  transition: transform 0.2s ease;
}
.controls button:hover {
  transform: scale(1.12);
}
.controls .play {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: var(--shadow);
}

/* 歌词栏 */
.lyric-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 18px;
  box-shadow: var(--shadow);
  min-height: 60px;
}
.wave {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 26px;
  flex-shrink: 0;
}
.wave i {
  width: 3px;
  height: 6px;
  border-radius: 3px;
  background: var(--accent);
}
.wave.active i {
  animation: wave 1s ease-in-out infinite;
}
.wave.active i:nth-child(1) { animation-delay: 0ms; }
.wave.active i:nth-child(2) { animation-delay: 150ms; }
.wave.active i:nth-child(3) { animation-delay: 300ms; }
.wave.active i:nth-child(4) { animation-delay: 200ms; }
.wave.active i:nth-child(5) { animation-delay: 100ms; }
@keyframes wave {
  0%, 100% { height: 6px; }
  50% { height: 24px; }
}
.lyric-text {
  margin: 0;
  flex: 1;
  min-width: 0;
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 3px;
  vertical-align: -2px;
  background: var(--accent);
  animation: blink 0.9s steps(1) infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
@media (max-width: 560px) {
  .player-card {
    min-height: 260px;
    padding: 18px;
  }
  .cover {
    width: 88px;
    height: 88px;
  }
}
</style>
