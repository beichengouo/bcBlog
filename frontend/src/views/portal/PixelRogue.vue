<template>
  <div ref="pageRef" class="game-site">
    <section id="game-intro" class="game-hero">
      <GameHeroMotion />
      <div class="game-hero-shade"></div>
      <div class="game-celestial" aria-hidden="true"><div class="celestial-ring"></div><div class="celestial-ring inner"></div><span>✦</span><small>RONDO / STARLIGHT</small></div>
      <div class="game-hero-content"><p class="game-eyebrow">INDEPENDENT ACTION ROGUELITE <span>独立开发 · 持续更新</span></p><h1>轮舞于<br /><em>星辉尽头</em></h1><p class="game-english">{{ pixelrogue.englishTitle }}</p><p class="game-lead">星光会熄灭，同行的故事不会。<br />踏入未知的房间，缔结属于你的命运。</p><div class="game-actions"><a href="#game-release" class="game-button">获取游戏 <span aria-hidden="true">↓</span></a><a :href="pixelrogue.repository" target="_blank" rel="noopener noreferrer" class="game-text-link">查看游戏项目 ↗</a></div></div>
      <div class="game-live-editions" aria-live="polite"><span class="edition-caption">AVAILABLE EDITIONS</span><template v-if="!loading && !failed && Object.keys(packages).length"><a v-for="item in packages" :key="item.platform" href="#game-release"><i aria-hidden="true"></i>{{ item.platform === 'pc' ? 'Windows' : 'Android' }}<strong>{{ item.version }}</strong></a></template><span v-else>{{ loading ? '正在读取发行版本…' : failed ? '发行信息暂不可用' : '安装包暂未发布 · 敬请期待' }}</span></div>
      <span class="game-hero-index" aria-hidden="true">01 — THE JOURNEY BEGINS</span>
    </section>

    <nav class="game-section-nav" aria-label="游戏介绍目录"><span class="game-page-progress" :style="{ '--page-progress': progress }" aria-hidden="true"></span><a v-for="section in sections" :key="section.id" :href="'#' + section.id" :class="{ 'is-current': activeSection === section.id }" :aria-current="activeSection === section.id ? 'location' : undefined" @click="activeSection = section.id">{{ section.label }}</a></nav>

    <section id="game-world" class="game-section game-world">
      <div class="game-section-heading game-reveal"><span>01 / WORLD</span><h2>世界尚有回响，<br />旅途未到终章。</h2><p>俯视角动作肉鸽，穿行于两章冒险的四阶段房间图。<br />从苏醒林地出发，走入失名星城，在战斗与选择间寻找归途。</p></div>
      <div class="game-world-grid"><figure class="game-reveal" v-for="chapter in chapters" :key="chapter.title"><img :src="chapter.image" :alt="chapter.title + '场景美术'" loading="lazy" width="1440" height="810" /><figcaption><div><small>{{ chapter.english }}</small><h3>{{ chapter.title }}</h3></div><span>{{ chapter.description }}</span></figcaption></figure></div><p class="game-art-note">场景美术展示 · 非实机截图</p>
    </section>

    <section id="game-characters" class="game-section game-characters" :style="{ '--hero-accent': activeHero.color }">
      <div class="game-section-heading game-reveal"><span>02 / COMPANIONS</span><h2>与你同行，<br />而非为你存在。</h2><p>三位可操作角色，三种战斗节奏。<br />她们有各自的意愿、记忆与选择；契约，是旅程的起点。</p></div>
      <div class="game-character-stage game-reveal">
        <div class="game-character-art"><img :key="activeHero.id" :src="activeHero.image" :alt="activeHero.name + '角色立绘'" loading="lazy" width="720" height="1080" /></div>
        <div class="game-character-copy"><div :key="activeHero.id" class="game-character-story"><span class="game-character-role">{{ activeHero.role }}</span><h3>{{ activeHero.name }}</h3><p class="game-character-motif">{{ activeHero.motif }}</p><p>{{ activeHero.description }}</p><div class="game-character-focus">{{ activeHero.focus }}</div></div><div class="game-character-switch" role="group" aria-label="选择介绍的角色"><button v-for="(hero, index) in pixelrogue.heroes" :key="hero.id" type="button" :aria-pressed="activeHero.id === hero.id" :class="{ selected: activeHero.id === hero.id }" @click="activeHeroId = hero.id"><span>0{{ index + 1 }}</span>{{ hero.name }}</button></div></div>
      </div>
    </section>

    <section id="game-play" class="game-section game-play">
      <div class="game-section-heading game-reveal"><span>03 / GAMEPLAY</span><h2>每一次出发，<br />都写下不同的答案。</h2></div>
      <div class="game-play-grid"><article class="game-reveal" v-for="(feature, index) in features" :key="feature.title"><span class="game-feature-number">0{{ index + 1 }}</span><div><h3>{{ feature.title }}</h3><p>{{ feature.description }}</p></div></article></div>
      <div class="game-journey game-reveal"><span>营地准备</span><i aria-hidden="true">→</i><span>房间探索</span><i aria-hidden="true">→</i><span>首领挑战</span><i aria-hidden="true">→</i><span>撤离归营</span></div>
    </section>

    <section id="game-release" class="game-section game-release">
      <div class="game-release-art game-reveal"><img src="/games/pixelrogue/city.webp" alt="失名星城发行主题美术" loading="lazy" /><span>YOUR NEXT JOURNEY<small>星途，由此启程</small></span></div>
      <div class="game-release-copy game-reveal"><span class="game-eyebrow">THE NEXT CHAPTER / 游戏发行</span><h2>同行焕新，星途再启。</h2><p>三位伙伴与六套战斗外观、十张正逆位命运牌、六位首领，以及营地中的新故事。旅途仍在完善，欢迎来看看它正在长成的样子。</p><div class="game-release-status"><span></span>源码已公开 · 平台发行信息见下方</div><div class="game-actions"><a class="game-button" :href="pixelrogue.repository" target="_blank" rel="noopener noreferrer">前往项目仓库 <span aria-hidden="true">↗</span></a><a class="game-text-link" :href="pixelrogue.releases" target="_blank" rel="noopener noreferrer">关注后续发行 ↗</a></div><small>Windows / Android 安装包由站长独立发布，各平台版本以下方下载信息为准。</small></div>
      <GameDownloads :packages="packages" :loading="loading" :failed="failed" @retry="load" />
    </section>
    <div class="game-colophon"><span>Rondo at Starlight’s End</span><router-link to="/portal">回到小站 ↗</router-link></div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useGameReleases } from '@/composables/useGameReleases'
const { packages, loading, failed, load } = useGameReleases()
import { pixelrogue } from '@/config/pixelrogue'
import GameHeroMotion from '@/components/portal/GameHeroMotion.vue'
import GameDownloads from '@/components/portal/GameDownloads.vue'
import { useGamePageMotion } from '@/composables/useGamePageMotion'
const pageRef = ref(null)
const { activeSection, progress } = useGamePageMotion(pageRef)
const sections = [{ id: 'game-intro', label: '序章' }, { id: 'game-world', label: '世界' }, { id: 'game-characters', label: '同行者' }, { id: 'game-play', label: '玩法' }, { id: 'game-release', label: '版本与项目' }]
const activeHeroId = ref(pixelrogue.heroes[0].id)
const activeHero = computed(() => pixelrogue.heroes.find((hero) => hero.id === activeHeroId.value) || pixelrogue.heroes[0])
const chapters = [
  { title: '苏醒林地', english: 'THE AWAKENING WOODS', image: '/games/pixelrogue/forest.webp', description: '林地、湿地与遗迹，等待第一段足迹。' },
  { title: '失名星城', english: 'THE NAMELESS STAR CITY', image: '/games/pixelrogue/city.webp', description: '走入星城，在遗落的名字间继续前行。' }
]
const features = [
  { title: '房间之间，自选前路', description: '两章四阶段冒险，穿过战斗、奖励、祭坛与异闻房间。探索未知，也决定何时带着收获返航。' },
  { title: '技能与遗物，构筑你的节奏', description: '在营地配置 3 个小技能与 1 个大招，以技能专属天赋、装备、遗物与套装打造自己的战斗方式。' },
  { title: '命运正逆，选择各有重量', description: '十种塔罗拥有正逆位效果，搭配星座与角色共鸣，在收益与代价之间寻找属于本次远征的答案。' },
  { title: '成功归航，让成长留下', description: '成功撤离才能将剩余材料与绑定装备带回仓库。在营地炼金、制作与研究，为下一场旅途做好准备。' }
]
</script>

<style scoped src="@/styles/pixelrogue.css"></style>
