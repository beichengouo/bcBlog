<template>
  <section class="fate-showcase" aria-labelledby="fate-heading"><header><span>FATE / 命运回响</span><h2 id="fate-heading">命运的另一面，由你翻开。</h2><p>选择命运牌，点击卡面切换正位与逆位。</p></header><div class="fate-stage"><Transition name="fate-change" mode="out-in"><button :key="active.id" class="fate-flip" :class="{ reversed: reverse }" :aria-label="`${active.name}，当前${reverse ? '逆位' : '正位'}，点击翻转`" :aria-pressed="reverse" @click="reverse = !reverse"><span class="fate-rotor"><img class="fate-front" :src="active.image" alt="" /><img class="fate-back" :src="active.reverseImage" alt="" /></span></button></Transition><div class="fate-copy" aria-live="polite"><small>{{ reverse ? 'REVERSED / 逆位' : 'UPRIGHT / 正位' }}</small><h3>{{ active.name }}</h3><h4>{{ active.theme }}</h4><p>{{ reverse ? active.reverse : active.upright }}</p><button @click="reverse = !reverse">翻转命运牌 ↻</button><router-link to="/portal/games/pixelrogue/codex">查阅完整图鉴 ↗</router-link></div></div><div class="fate-selector" role="group" aria-label="选择命运牌"><button v-for="card in cards" :key="card.id" :aria-pressed="active.id === card.id" @click="choose(card)">{{ card.name }}</button></div></section>
</template>
<script setup>
import { ref } from 'vue'
import codex from '@/config/gameCodex.json'
const cards = codex.cards, active = ref(cards[0]), reverse = ref(false)
function choose(card) { active.value = card; reverse.value = false }
</script>
<style scoped>
.fate-showcase { max-width: 1200px; margin: 0 auto; padding: 70px 32px; border-top: 1px solid #ceb99433; background: radial-gradient(ellipse at 25% 50%,#77659b22,transparent 65%); }
header > span,.fate-copy small { font-size: 10px; color: #ceb994; letter-spacing: 3px; }
h2 { font: 400 clamp(26px,3vw,40px)/1.7 SimSun,serif; }
header p,.fate-copy p { font-size: 13px; color: #acb8cc; line-height: 2.2; }
.fate-stage { display: grid; grid-template-columns: 1fr 1fr; gap: 55px; align-items: center; margin: 40px 0; }
.fate-flip { display: block; border: 0; background: none; cursor: pointer; perspective: 1200px; width: 100%; height: 460px; padding: 0; }
.fate-rotor { display: block; position: relative; width: 100%; height: 100%; transform-style: preserve-3d; transition: transform .8s cubic-bezier(.22,.75,.16,1); }
.reversed .fate-rotor { transform: rotateY(180deg); }
.fate-rotor img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; backface-visibility: hidden; -webkit-backface-visibility: hidden; }
.fate-back { transform: rotateY(180deg); }
.fate-copy { min-width: 0; padding: 28px; background: #0d1728e6; border: 1px solid #ceb99433; border-radius: 16px; }
.fate-copy h3 { font: 400 38px SimSun,serif; margin: 20px 0; }.fate-copy h4 { font-size: 14px; font-weight: 400; color: #ceb994; }
.fate-copy p { min-height: 130px; }.fate-copy a { display: block; margin-top: 18px; color: #ceb994; font-size: 11px; }
.fate-copy button,.fate-selector button { padding: 11px 18px; border: 1px solid #ceb99455; background: #ceb9940a; color: #cbbda4; border-radius: 8px; cursor: pointer; }
.fate-selector { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }.fate-selector button[aria-pressed=true] { border-color: #ceb994; background: #ceb99428; color: #f4e5cc; }
button:focus-visible { outline: 2px solid #ceb994; outline-offset: 4px; }
.fate-change-enter-active,.fate-change-leave-active { transition: opacity .22s, translate .22s; }.fate-change-enter-from { opacity: 0; translate: 20px 0; }.fate-change-leave-to { opacity: 0; translate: -20px 0; }
@media(max-width:650px) { .fate-stage { grid-template-columns: 1fr; gap: 24px; }.fate-flip { height: 350px; }.fate-showcase { padding: 50px 24px; } }
@media(prefers-reduced-motion:reduce) { .fate-rotor,.fate-change-enter-active,.fate-change-leave-active { transition: none; } }
</style>
