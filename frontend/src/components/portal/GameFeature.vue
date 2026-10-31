<template>
  <section class="game-feature" aria-labelledby="game-feature-heading">
    <router-link class="game-feature-link" to="/portal/games/pixelrogue">
      <img src="/games/pixelrogue/hero.webp" alt="轮舞于星辉尽头的主菜单美术" loading="lazy" width="1920" height="1080" />
      <div class="game-feature-copy"><span class="game-feature-kicker">独立游戏 / 开发手记</span><h2 id="game-feature-heading">轮舞于星辉尽头</h2><p>在星光将尽之处，与你再启一段旅程。</p><span class="game-feature-action">走进游戏世界 <span aria-hidden="true">↗</span></span></div>
      <span class="game-feature-edition">{{ pixelrogue.version }}<small>源码开发版</small></span>
    </router-link>
  </section>
</template>

<script setup>
import { pixelrogue } from '@/config/pixelrogue'
</script>

<style scoped>
.game-feature { max-width: 1320px; padding: 0 20px; margin: 12px auto 32px; }
.game-feature-link { position: relative; display: flex; align-items: center; min-height: 250px; padding: 40px 44px; border-radius: 20px; overflow: hidden; isolation: isolate; color: #f2ecdf; border: 1px solid #c7b79440; background: #121624; }
.game-feature-link::after { content: ''; position: absolute; inset: 0; z-index: -1; background: linear-gradient(90deg, #0b111aee 15%, #0d1523b8 55%, #0b111a30), linear-gradient(0deg, #0a1020, transparent 70%); }
.game-feature-link img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center 38%; z-index: -2; transition: scale .6s; }
.game-feature-link:hover img { scale: 1.025; }
.game-feature-kicker { font-size: 10px; letter-spacing: 3px; color: #d2c3a4; }
.game-feature-copy h2 { margin: 16px 0 12px; font-family: 'Noto Serif SC', SimSun, serif; font-size: clamp(26px, 3vw, 38px); font-weight: 400; letter-spacing: 3px; }
.game-feature-copy p { font-size: 12px; line-height: 1.9; color: #c4cad5; }
.game-feature-action { display: inline-flex; align-items: center; gap: 28px; margin-top: 12px; font-size: 11px; color: #e6d5b7; }
.game-feature-edition { margin-left: auto; padding-left: 20px; font-family: Georgia, serif; font-size: 34px; color: #e8dbbe; }
.game-feature-edition small { display: block; font-family: inherit; font-size: 10px; letter-spacing: 2px; margin-top: 10px; color: #c4cad5; }
@media (max-width: 600px) { .game-feature-link { padding: 30px 24px; min-height: 240px; } .game-feature-edition { display: none; } .game-feature-copy h2 { letter-spacing: 1px; } }
</style>
