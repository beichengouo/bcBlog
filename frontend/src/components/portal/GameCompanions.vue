<template>
  <div class="companions" :style="{ '--accent': active.color }">
    <TransitionGroup name="lineup" tag="div" class="companion-lineup" role="group" aria-label="切换同行者">
      <button v-for="hero in lineup" :key="hero.id" class="companion-portrait" :class="{ active: hero.id === selected }" :aria-pressed="hero.id === selected" :aria-label="`展示${hero.name}`" @click="selected = hero.id">
        <div class="portrait-frame"><Transition name="costume"><img :key="hero.id + (legendary && hero.id === selected ? 'set' : 'normal')" :src="legendary && hero.id === selected ? codex.costumes[hero.id].image : hero.image" :alt="hero.id === selected ? `${hero.name} · ${legendary ? codex.costumes[hero.id].name : '初始装束'}` : `${hero.name}剪影`" loading="eager" /></Transition></div>
        <span>{{ hero.name }}<small>{{ hero.id === selected ? '正在展示' : '点击唤醒' }}</small></span>
      </button>
    </TransitionGroup>
    <div class="companion-details">
      <div><span class="companion-role">{{ active.role }}</span><h3>{{ active.name }}</h3><p class="companion-motif">{{ active.motif }}</p><p>{{ active.description }}</p></div>
      <div class="companion-costume">
        <div class="costume-tabs" role="group" aria-label="选择角色装束"><button :aria-pressed="!legendary" @click="legendary = false">初始装束</button><button :aria-pressed="legendary" @click="legendary = true">传说套装 ✦</button></div>
        <h4>{{ legendary ? codex.costumes[selected].name : '旅途的最初模样' }}</h4>
        <p>{{ legendary ? codex.costumes[selected].effect : active.focus }}</p>
        <router-link to="/portal/games/pixelrogue/codex">查阅装备与命运图鉴 ↗</router-link>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { pixelrogue } from '@/config/pixelrogue'
import codex from '@/config/gameCodex.json'
const selected = ref(pixelrogue.heroes[0].id), legendary = ref(false)
const active = computed(() => pixelrogue.heroes.find(hero => hero.id === selected.value))
const lineup = computed(() => { const others = pixelrogue.heroes.filter(hero => hero.id !== selected.value); return [others[0], active.value, others[1]] })
</script>
<style scoped>
.companions { border: 1px solid #ceb99440; border-radius: 24px; overflow: hidden; background: radial-gradient(ellipse at 50% 30%, #7a82b333, transparent 60%), #101725; }
.companion-lineup { display: flex; align-items: end; padding: 30px 20px 0; min-width: 0; }
.companion-portrait { position: relative; border: 0; background: none; padding: 0; min-width: 0; flex: 1; cursor: pointer; color: #b1bdd1; }
.companion-portrait.active { flex: 1.7; }
.companion-portrait img { width: 100%; height: clamp(270px, 44vw, 580px); object-fit: contain; object-position: bottom; filter: brightness(0) drop-shadow(0 0 2px #b2b9d7); opacity: .5; transform: scale(.85); transform-origin: bottom; transition: filter .5s, opacity .5s, transform .5s; }
.companion-portrait:hover img { opacity: .8; }
.companion-portrait.active img { filter: drop-shadow(0 0 32px #9cadde22); opacity: 1; transform: none; }
.companion-portrait > span { display: block; padding: 16px 5px; font-family: SimSun, serif; font-size: 19px; background: linear-gradient(transparent, #0b101b); }
.companion-portrait small { display: block; font-family: sans-serif; font-size: 10px; color: #8493ad; margin-top: 8px; }
.companion-portrait.active > span { color: var(--accent); }
.companion-details { display: grid; grid-template-columns: 1fr 1fr; gap: 45px; padding: 36px; border-top: 1px solid #ceb99426; }
.companion-role { font-size: 11px; color: var(--accent); letter-spacing: 3px; }
.companion-details h3 { font-size: 36px; font-family: SimSun, serif; font-weight: 400; margin: 15px 0; }
.companion-details p { color: #a9b5c9; font-size: 12px; line-height: 2; }
.companion-details .companion-motif { font-size: 18px; color: var(--accent); }
.costume-tabs { display: flex; gap: 10px; }
.costume-tabs button { border: 1px solid #ceb99444; padding: 10px 16px; border-radius: 8px; color: #b4bed0; background: none; cursor: pointer; }
.costume-tabs button[aria-pressed=true] { color: #eed7ae; border-color: #ceb994; background: #ceb99414; }
.companion-costume h4 { color: #e9d2ab; font-size: 20px; font-family: SimSun, serif; font-weight: 400; margin: 24px 0 12px; }
.companion-costume a { display: inline-block; margin-top: 12px; color: #dec59e; font-size: 11px; }
button:focus-visible { outline: 2px solid #ceb994; outline-offset: -3px; }
@media(max-width:600px) { .companion-lineup { padding: 20px 4px 0; } .companion-portrait img { height: 300px; } .companion-portrait.active { flex: 2.2; } .companion-portrait > span { font-size: 14px; } .companion-details { grid-template-columns: 1fr; padding: 24px; gap: 24px; } }
@media(prefers-reduced-motion:reduce) { .companion-portrait img { transition: none; } }
.portrait-frame { position: relative; height: clamp(270px,44vw,580px); }
.companion-portrait, .companion-portrait.active { flex: 1; }
.companion-portrait .portrait-frame img { position: absolute; inset: 0; height: 100%; }
.lineup-move { transition: transform .7s cubic-bezier(.22,.75,.16,1); }
.costume-enter-active, .costume-leave-active { transition: opacity .5s ease, filter .5s ease !important; }
.costume-enter-from, .costume-leave-to { opacity: 0 !important; }
@media(max-width:600px) { .portrait-frame { height: 300px; } }
@media(prefers-reduced-motion:reduce) { .lineup-move,.costume-enter-active,.costume-leave-active { transition: none !important; } }
</style>
