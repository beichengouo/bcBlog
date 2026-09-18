<template>
  <div class="sandbox">
    <header class="sandbox-head">
      <div class="head-title">
        <!-- 多个世界时标题本身就是切换入口；只有一个世界时用纯文字标题 -->
        <el-dropdown
          v-if="worlds.length > 1"
          trigger="click"
          placement="bottom-start"
          @command="onSwitchWorld"
        >
          <button type="button" class="world-title-btn">
            <span class="world-title-name">{{ currentWorldName }}</span>
            <svg class="world-title-caret" viewBox="0 0 24 24" width="16" height="16" fill="none"
                 stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6 9l6 6 6-6" />
            </svg>
          </button>
          <template #dropdown>
            <el-dropdown-menu class="world-menu">
              <el-dropdown-item
                v-for="item in worlds"
                :key="'w-' + item.id"
                :command="item.id"
                :class="{ 'is-current': item.id === currentWorldId }"
              >
                <span class="world-option-name">{{ item.name || ('世界 ' + item.id) }}</span>
                <span v-if="item.enabled !== 1" class="world-option-tag">已停止</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <h1 v-else>{{ world.name || '沙盒世界' }}</h1>
      </div>
      <p>{{ world.description || '管理员还没有填写世界简介' }}</p>
      <span v-if="!enabled" class="paused">
        {{ currentWorldStopped ? '这个世界已停止运行，当前只展示历史记录' : 'AI 自动行动已暂停，当前只展示历史记录' }}
      </span>
    </header>

    <section class="map-card">
      <div v-if="!world.mapImage" class="map-empty">
        <p>管理员还没有上传地图背景</p>
        <p class="sub">可以在后台「站点管理 → 沙盒世界」上传地图并添加地点</p>
      </div>
      <div v-else class="map-stage" @click="activeLocationId = null">
        <img class="map-bg" :src="world.mapImage" alt="地图" />
        <!-- 旅人纪闻：当天世界上发生的大事 -->
        <div v-if="newsTitle && news.length" class="news-card" :class="{ collapsed: newsCollapsed }" @click.stop>
          <div class="news-head" @click="newsCollapsed = !newsCollapsed">
            <span class="news-name">{{ newsTitle }}</span>
            <span class="news-date">{{ todayText }}</span>
            <span class="news-toggle">{{ newsCollapsed ? '展开' : '收起' }}</span>
          </div>
          <div v-show="!newsCollapsed" class="news-list">
            <button
              v-for="item in news"
              :key="'news-' + item.id"
              type="button"
              class="news-item"
              :title="item.content || item.title"
              @click="focusNews(item)"
            >
              <span class="news-level" :class="'lv-' + (item.level || 1)"></span>
              <span class="news-text">{{ item.title }}</span>
              <span v-if="item.locationName" class="news-place">{{ item.locationName }}</span>
            </button>
          </div>
        </div>

        <!-- 地点区域：套索画的多边形用 SVG 描边显示；单点地点画成小圆点 -->
        <svg class="area-layer" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <template v-for="loc in locations" :key="'shape-' + loc.id">
            <polygon
              v-if="locationPolygon(loc)"
              :points="polygonPoints(loc)"
              class="area-shape"
              :class="{ on: activeLocationId === loc.id, flash: flashLocationId === loc.id }"
              @click.stop="toggleLocation(loc)"
            />
            <circle
              v-else
              :cx="Number(loc.x == null ? 50 : loc.x)"
              :cy="Number(loc.y == null ? 50 : loc.y)"
              r="2.4"
              class="area-shape point"
              :class="{ on: activeLocationId === loc.id, flash: flashLocationId === loc.id }"
              @click.stop="toggleLocation(loc)"
            />
          </template>
        </svg>

        <!-- 地点名标签：放在区域标注点上（形心；凹多边形退回内部点），默认只显示地名与图标 -->
        <div
          v-for="loc in locations"
          :key="'area-' + loc.id"
          class="area"
          :class="{ on: activeLocationId === loc.id, flash: flashLocationId === loc.id }"
          :style="areaStyle(loc)"
          :title="locationTitle(loc)"
          @click.stop="toggleLocation(loc)"
        >
          <span class="area-label">
            <LocationIcon :icon="loc.icon" :size="15" />
            <span class="area-name">{{ loc.name }}</span>
            <span v-if="charactersIn(loc).length" class="area-count">{{ charactersIn(loc).length }}</span>
          </span>
        </div>

        <!-- 点击地点后，该地的角色头像出现在区域下方 -->
        <div v-if="activeLocation" class="area-actors" :style="actorsStyle" @click.stop>
          <button
            v-for="member in charactersIn(activeLocation)"
            :key="'aa-' + member.id"
            type="button"
            class="area-actor"
            :class="{ on: activeId === member.id }"
            :title="`${member.name} · ${placeText(member.locationName, member.subLocation)}`"
            @click="selectFromLocation(member)"
          >
            <img v-if="member.avatar" class="area-actor-img" :src="member.avatar" :alt="member.name" />
            <span v-else class="area-actor-img fallback">{{ (member.name || '?').slice(0, 1) }}</span>
            <span class="area-actor-name">{{ member.name }}</span>
          </button>
          <span v-if="!charactersIn(activeLocation).length" class="area-empty">这里暂时没有角色</span>
        </div>

        <button type="button" class="map-zoom-btn" @click="openMapViewer">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
          </svg>
          <span>放大查看</span>
        </button>
      </div>
    </section>

    <!-- 全屏地图：手机上看地图更方便 -->
    <SandboxMapViewer
      v-model:visible="mapViewerVisible"
      :map-image="world.mapImage"
      :title="world.name"
      :locations="locations"
      :characters="characters"
      :active-id="activeId"
      @select="onViewerSelect"
    />

    <!-- 最新动态：世界最近发生的 3 件事，各自一个容器、按三行纵向排列 -->
    <section class="latest-block">
      <div class="latest-block-head">
        <span class="latest-label">最新动态</span>
        <span class="muted">世界里最近发生的几件事</span>
      </div>
      <div class="latest-grid">
        <article v-for="act in latestActs" :key="'latest-' + act.id" class="card latest-card">
          <span class="latest-time">{{ act.createTime }}</span>
          <span class="latest-text" :title="act.summary || act.actions">
            <strong>{{ characterName(act.characterId) }}</strong>
            <span class="muted">在 {{ placeText(act.locationName, act.subLocation) }}</span><template
              v-if="act.summary"
            >：{{ act.summary }}</template>
          </span>
          <button type="button" class="ghost-btn latest-btn" @click="viewCharacterActs(act.characterId)">
            查看 TA 的行动
          </button>
        </article>
        <article v-if="!latestActs.length" class="card latest-card">
          <span class="muted">还没有行动记录</span>
        </article>
      </div>
    </section>

    <section ref="profileRef" class="card panel">
      <div class="panel-head">
        <h3>角色档案</h3>
        <div class="panel-tools">
          <input v-model="keyword" class="char-search" type="search" placeholder="搜索角色名 / 称号 / 所在地" />
          <!-- 收起只作用于"角色详情"；看「全部」时没有详情可收，按钮就不显示 -->
          <button v-if="active" class="ghost-btn" @click="collapsed = !collapsed">
            {{ collapsed ? '展开' : '收起' }}
          </button>
        </div>
      </div>

      <!-- 面板主体不随收起按钮消失：收起只收角色详情，行动记录（主要阅读内容）始终保留 -->
      <div class="panel-body">
        <div class="char-chips">
          <!-- 「全部」= 只按"全部角色"看行动记录；点具体角色才展开上面的角色详情 -->
          <button class="char-chip" :class="{ on: !activeId }" @click="selectAllCharacters">
            <span class="chip-avatar fallback">全</span>
            <span class="chip-text">
              <span class="chip-name">全部</span>
              <span class="chip-loc">所有角色的行动</span>
            </span>
          </button>
          <button
            v-for="c in filteredCharacters"
            :key="'chip-' + c.id"
            class="char-chip"
            :class="{ on: activeId === c.id }"
            @click="selectCharacter(c)"
          >
            <img v-if="c.avatar" class="chip-avatar" :src="c.avatar" :alt="c.name" />
            <span v-else class="chip-avatar fallback">{{ (c.name || '?').slice(0, 1) }}</span>
            <span class="chip-text">
              <span class="chip-name">{{ c.name }}</span>
              <span class="chip-loc">{{ c.locationName || '尚未行动' }}</span>
            </span>
          </button>
          <div v-if="!filteredCharacters.length" class="muted">
            {{ characters.length ? '没有匹配的角色' : '管理员还没有添加角色' }}
          </div>
        </div>

        <!-- 角色详情：收起按钮只收这一块，下面的行动记录始终保留 -->
        <div v-if="active" v-show="!collapsed" class="detail">
          <div class="detail-head">
            <img v-if="active.avatar" class="detail-avatar" :src="active.avatar" :alt="active.name" />
            <div class="detail-title">
              <h2>{{ active.name }}</h2>
              <p class="title">{{ active.title || '旅行者' }}</p>
              <p class="muted">
                <template v-if="activeTravel">
                  正在前往 {{ placeText(activeTravel.location, activeTravel.subLocation) }}（预计 {{ activeTravel.arriveAt }} 抵达）
                </template>
                <template v-else>当前位置：{{ characterPlace(active) }}</template>
              </p>
              <p class="muted">下次行动：{{ nextRunText(active) }}</p>
            </div>
            <div class="status">
              <span class="chip coin-chip">金币 {{ active.coins || 0 }}</span>
              <span class="chip combat-chip">战斗力 {{ active.combatPower == null ? 10 : active.combatPower }}</span>
              <span class="chip goal-chip">目标 {{ active.goal || '还没有明确目标' }}</span>
              <span v-for="(value, key) in active.status || {}" :key="key" class="chip">
                {{ key }} {{ value }}
              </span>
            </div>
          </div>

          <p v-if="active.appearance" class="appearance">{{ active.appearance }}</p>

          <!-- 想法：角色对实力/财富的看法会随经历缓慢改变，这里展示当前态度与最近几次变化 -->
          <div
            v-if="active.powerView || active.wealthView || (active.recentAttitudes || []).length"
            class="attitude"
          >
            <h3>TA 的想法</h3>
            <div class="attitude-views">
              <span v-if="active.powerView" class="attitude-chip">
                <span class="label">对实力</span>{{ active.powerView }}
              </span>
              <span v-if="active.wealthView" class="attitude-chip">
                <span class="label">对财富</span>{{ active.wealthView }}
              </span>
            </div>
            <div v-if="(active.recentAttitudes || []).length" class="attitude-history">
              <div v-for="item in active.recentAttitudes" :key="'att-' + item.id" class="attitude-item">
                <span class="attitude-tag" :class="item.kind === 'power' ? 'power' : 'wealth'">
                  {{ item.kind === 'power' ? '实力' : '财富' }}
                </span>
                <div class="attitude-body">
                  <div class="attitude-change">
                    <span class="old">{{ item.oldView || '（还没想过）' }}</span>
                    <span class="arrow">→</span>
                    <span class="new">{{ item.newView }}</span>
                  </div>
                  <div class="attitude-reason">
                    {{ item.reason }}
                    <span v-if="item.major === 1" class="major">重大经历</span>
                  </div>
                </div>
                <span class="time">{{ (item.createTime || '').slice(5, 16) }}</span>
              </div>
            </div>
            <p v-else class="muted">TA 的想法还没有因为什么经历改变过。</p>
          </div>

          <div class="contribute">
            <div class="contribute-head">
              <strong>为 TA 贡献金币</strong>
              <span class="muted">
                1 积分 = {{ coinRate }} 金币<template v-if="isLogin">（我的积分：{{ myPoints }}）</template>
              </span>
            </div>
            <div class="contribute-actions">
              <button
                v-for="p in [1, 5, 10]"
                :key="'preset-' + p"
                class="chip-btn"
                :class="{ on: contributePoints === p }"
                @click="contributePoints = p"
              >
                {{ p }} 积分
              </button>
              <input v-model.number="contributePoints" class="num-input" type="number" min="1" max="100" />
              <button class="send" :disabled="contributing" @click="onContribute">
                {{ contributing ? '贡献中…' : `贡献 ${contributePoints || 0} 积分 → ${(contributePoints || 0) * coinRate} 金币` }}
              </button>
            </div>
            <p class="muted">金币会用在角色的日常开销上（吃饭、住店、买材料），角色也会靠做工或接委托把它赚回来。</p>
          </div>

          <div v-if="(active.recentCoins || []).length" class="coin-history">
            <h3>金币记录</h3>
            <div v-for="log in active.recentCoins" :key="'coin-' + log.id" class="coin-item">
              <span class="coin-type">{{ coinTypeText(log.type) }}</span>
              <span class="coin-delta" :class="log.coins > 0 ? 'plus' : 'minus'">
                {{ log.coins > 0 ? '+' : '' }}{{ log.coins }}
              </span>
              <span class="coin-remark">{{ log.remark }}</span>
              <span class="time">{{ log.createTime }}</span>
            </div>
          </div>

          <div v-if="(active.relations || []).length" class="relations">
            <h3>与其他角色的关系</h3>
            <div v-for="rel in active.relations" :key="'rel-' + rel.targetId" class="relation-item">
              <img v-if="rel.targetAvatar" class="relation-avatar" :src="rel.targetAvatar" alt="" />
              <span v-else class="relation-avatar fallback">{{ (rel.targetName || '?').slice(0, 1) }}</span>
              <div class="relation-main">
                <div class="relation-name">
                  {{ rel.targetName }}
                  <span class="muted">
                    {{ rel.targetTitle || '旅行者' }} · {{ rel.targetLocation || '行踪不明' }}
                    <template v-if="distanceToCharacter(rel.targetId)">
                      · {{ distanceToCharacter(rel.targetId) }}
                    </template>
                  </span>
                </div>
                <div class="relation-bar">
                  <div class="relation-track">
                    <div
                      class="relation-fill"
                      :style="{ width: favorPercent(rel.favor) + '%', background: favorColor(rel.favor) }"
                    ></div>
                  </div>
                  <span class="relation-value">好感 {{ rel.favor }} · {{ rel.favorLevel }}</span>
                  <span
                    v-if="rel.lastChange"
                    class="favor-delta"
                    :class="rel.lastChange > 0 ? 'plus' : 'minus'"
                  >
                    较上次 {{ rel.lastChange > 0 ? '+' : '' }}{{ rel.lastChange }}
                  </span>
                </div>
                <div class="relation-reverse">TA 对你的好感：{{ rel.reverseFavor }} · {{ rel.reverseFavorLevel }}</div>
                <div v-if="rel.remark" class="relation-remark">印象：{{ rel.remark }}</div>
              </div>
            </div>
          </div>

          <div class="backpack">
            <div class="backpack-head">
              <h3>背包</h3>
              <span class="backpack-meta">
                {{ (active.items || []).length }} 种 · 共 {{ totalItemCount }} 件
              </span>
            </div>
            <div v-if="(active.items || []).length" class="item-grid">
              <button
                v-for="item in active.items"
                :key="'item-' + item.id"
                type="button"
                class="item-slot"
                :class="['rarity-' + (item.rarity || 1), { on: selectedItem && selectedItem.id === item.id }]"
                :title="item.name"
                @click="selectedItem = item"
              >
                <span class="slot-icon">
                  <img v-if="item.icon" :src="item.icon" :alt="item.name" />
                  <template v-else>{{ emojiForItem(item.name) }}</template>
                </span>
                <span class="slot-name">{{ item.name }}</span>
                <span v-if="(item.quantity || 1) > 1" class="slot-qty">{{ item.quantity }}</span>
              </button>
            </div>
            <div v-else class="backpack-empty">背包空空的，等它出门捡点什么吧</div>

            <div
              v-if="selectedItem"
              class="item-detail"
              :class="'rarity-' + (selectedItem.rarity || 1)"
            >
              <span class="detail-item-icon">
                <img v-if="selectedItem.icon" :src="selectedItem.icon" :alt="selectedItem.name" />
                <template v-else>{{ emojiForItem(selectedItem.name) }}</template>
              </span>
              <div class="detail-main">
                <div class="detail-name">
                  {{ selectedItem.name }}
                  <span class="rarity-tag">{{ rarityMeta(selectedItem.rarity).name }}</span>
                </div>
                <div class="detail-qty">数量：{{ selectedItem.quantity || 1 }}</div>
                <div class="detail-desc">
                  {{ selectedItem.description || '还没有关于这件物品的说明。' }}
                </div>
              </div>
            </div>
          </div>

          <div v-if="(active.recentMemories || []).length" class="memories">
            <h3>最近的记忆</h3>
            <div v-for="memory in active.recentMemories" :key="'mem-' + memory.id" class="memory-item">
              <div class="memory-date">{{ memory.memoryDate }}</div>
              <div class="memory-text">{{ memory.summary }}</div>
            </div>
          </div>

        </div>
        <div v-else-if="!collapsed" class="muted panel-hint">
          当前展示全部角色的行动记录；点上方角色名可以只看某个人。
        </div>

        <!-- 行动记录：原来独立的「行动时间线」合并到这里，每页 5 条 + 页码翻页 -->
        <div class="acts">
          <div class="acts-head">
            <h3>{{ active ? active.name + ' 的行动记录' : '全部角色的行动记录' }}</h3>
            <div class="acts-tools">
              <el-select
                v-model="locationFilter"
                class="loc-filter"
                clearable
                placeholder="全部地点"
                @change="loadTimeline(true)"
              >
                <el-option v-for="loc in locations" :key="'lf-' + loc.id" :label="loc.name" :value="loc.name" />
              </el-select>
              <span class="muted acts-total">共 {{ timelineTotal }} 条</span>
            </div>
          </div>
          <div v-if="!timeline.length" class="muted">还没有行动记录</div>
          <div v-for="act in timeline" :key="act.id" class="timeline-item">
            <!-- 行动记录里仍按"这一条是什么时候生成的"显示：
                 管理员点「立即执行」时会让下一条行动提前发生，用时段表示会重叠、反而更乱 -->
            <div class="timeline-time">{{ act.createTime }}</div>
            <div class="timeline-content">
              <div class="timeline-title">
                <strong>{{ characterName(act.characterId) }}</strong>
                <span class="muted">在 {{ placeText(act.locationName, act.subLocation) }}</span>
                <span v-if="act.coinChange" class="coin-delta inline" :class="act.coinChange > 0 ? 'plus' : 'minus'">
                  {{ act.coinChange > 0 ? '+' : '' }}{{ act.coinChange }} 金币
                </span>
                <span v-if="act.companions" class="companion-tag inline">与 {{ act.companions }} 互动</span>
                <span v-if="act.favorChange" class="favor-tag inline">好感 {{ act.favorChange }}</span>
                <span v-if="act.reaction === 1" class="react-tag inline">回应</span>
                <span v-if="act.itemChange" class="item-tag inline">{{ act.itemChange }}</span>
                <span v-if="act.combatChange" class="combat-tag inline">战斗力 {{ act.combatChange > 0 ? "+" : "" }}{{ act.combatChange }}</span>
                <span v-if="moveKmOf(act) >= 1" class="move-tag inline">移动 {{ formatKm(moveKmOf(act)) }}</span>
                <span v-if="act.newsRef" class="news-tag inline">听闻 · {{ act.newsRef }}</span>
              </div>
              <div class="act-body">{{ act.actions }}</div>
              <div v-if="act.innerVoice" class="voice">「{{ act.innerVoice }}」</div>
            </div>
          </div>
          <el-pagination
            v-if="timelineTotal > pageSize"
            class="acts-pager"
            :current-page="timelinePage"
            :page-size="pageSize"
            :total="timelineTotal"
            layout="prev, pager, next"
            @current-change="onTimelinePage"
          />
        </div>
      </div>
    </section>

    <!-- 旅人低语：后台总开关关闭时整块不显示（默认不渲染，接口确认开启后才出现） -->
    <section v-if="whisperEnabled" class="card panel">
      <div class="panel-head">
        <h3>旅人低语</h3>
        <div class="panel-tools">
          <label class="target-label">低语对象</label>
          <el-select
            v-model="whisperTargetId"
            class="target-select"
            placeholder="选择要互动的角色"
            filterable
            @change="loadWhispers"
          >
            <el-option v-for="c in characters" :key="'t-' + c.id" :label="c.name" :value="c.id">
              <span class="option-name">{{ c.name }}</span>
              <span class="option-loc">{{ c.locationName || '尚未行动' }}</span>
            </el-option>
          </el-select>
        </div>
      </div>

      <div class="panel-body">
        <p class="muted">
          登录后可以给指定角色留下一句话，{{ whisperPoints }} 积分一次，对方下一次行动时可能会看见它。
        </p>
        <div class="whisper-list">
          <div v-if="!whispers.length" class="muted">还没有人给这位角色留下低语</div>
          <div v-for="item in whispers" :key="item.id" class="whisper-item">
            <img v-if="item.userAvatar" class="whisper-avatar" :src="item.userAvatar" alt="" />
            <span v-else class="whisper-avatar fallback">{{ (item.userName || '旅').slice(0, 1) }}</span>
            <div class="whisper-main">
              <div class="whisper-user">
                {{ item.userName || '旅人' }}
                <span class="time">{{ item.createTime }}</span>
              </div>
              <div class="whisper-text">{{ item.content }}</div>
            </div>
          </div>
        </div>
        <div class="whisper-form">
          <textarea
            v-model="whisperText"
            maxlength="200"
            :placeholder="isLogin ? `对「${whisperTargetName}」说点什么…` : '登录后即可留下低语'"
          />
          <button class="send" :disabled="sending" @click="sendWhisper">
            {{ sending ? '发送中…' : `送给「${whisperTargetName}」（${whisperPoints} 积分）` }}
          </button>
        </div>
        <p v-if="isLogin" class="muted">当前积分：{{ myPoints }}</p>
      </div>
    </section>

    <!-- 旅人集市：AI 定时刷新的商品，商品用金币标价；角色会自己掏金币买，
         前台用户购买时按汇率把金币价折算成积分扣款，买下后直接赠送给角色（商品进角色背包） -->
    <section v-if="shopEnabled" class="card shop">
      <div class="shop-head">
        <h3>{{ shopTitle || '旅人集市' }}</h3>
        <span class="muted">
          角色会自己用金币买（{{ shopBuyPerDay > 0 ? ('每天最多 ' + shopBuyPerDay + ' 件') : '不限件数' }}）；
          你花积分折算买下送给角色，TA 下一次行动时会收到「来自异世界的礼物」
        </span>
      </div>
      <div v-if="!shopItems.length" class="muted shop-empty">集市今天还没开张</div>
      <div v-else class="shop-grid">
        <button
          v-for="item in shopItems"
          :key="'shop-' + item.id"
          type="button"
          class="shop-cell"
          :class="['r' + (item.rarity || 1), { soldout: item.stock <= 0 }]"
          :style="{ '--r-color': rarityMeta(item.rarity).color, '--r-border': rarityMeta(item.rarity).border }"
          @click="openBuy(item)"
        >
          <span class="shop-ribbon"></span>
          <span class="shop-rarity-badge">{{ rarityMeta(item.rarity).name }}</span>
          <span class="shop-icon-wrap">
            <span class="shop-icon">{{ emojiForItem(item.name) }}</span>
          </span>
          <span class="shop-name">{{ item.name }}</span>
          <span class="shop-desc">{{ item.description || '' }}</span>
          <span class="shop-price"><span class="coin-dot">✦</span>{{ item.price }} 金币</span>
          <span class="shop-stockbar"><i :style="{ width: stockPercent(item) + '%' }"></i></span>
          <span class="shop-stock" :class="{ out: item.stock <= 0 }">
            {{ item.stock <= 0 ? '已售罄' : '剩 ' + item.stock + ' / ' + item.totalStock }}
          </span>
          <span v-if="item.stock <= 0" class="shop-stamp">已售罄</span>
        </button>
      </div>
    </section>

    <el-dialog v-model="buyVisible" :title="buyItem ? '赠送「' + buyItem.name + '」' : '赠送'" width="520px">
      <div v-if="buyItem" class="buy-body">
        <div class="buy-top">
          <span class="shop-icon big">{{ emojiForItem(buyItem.name) }}</span>
          <div class="buy-info">
            <div class="buy-name" :style="{ color: rarityMeta(buyItem.rarity).color }">
              {{ buyItem.name }}
              <span class="buy-rarity">{{ rarityMeta(buyItem.rarity).name }}</span>
            </div>
            <p class="buy-desc">{{ buyItem.description || '（这件商品没有留下描述）' }}</p>
            <div class="buy-price">
              <span>{{ buyItem.price }} 金币</span>
              <span class="muted">≈ {{ pointsOfItem(buyItem.price) }} 积分</span>
              <span class="muted">剩余 {{ buyItem.stock }} / {{ buyItem.totalStock }}</span>
              <span class="muted" v-if="isLogin">我的积分：{{ myPoints }}</span>
            </div>
          </div>
        </div>

        <div class="buy-block">
          <div class="buy-block-title">赠送记录</div>
          <div v-if="!(buyItem.orders || []).length" class="muted">还没有人赠送过这件商品</div>
          <div v-else class="buy-orders">
            <span v-for="order in buyItem.orders" :key="'o-' + order.id" class="buy-order">
              <template v-if="order.buyerType === 'character'">
                {{ order.characterName }} 自己在集市买下了它
              </template>
              <template v-else>
                {{ order.userName || '一位旅人' }} 送给了 {{ order.characterName }}
              </template>
            </span>
          </div>
        </div>

        <div class="buy-block">
          <div class="buy-block-title">送给谁</div>
          <el-select v-model="buyCharacterId" placeholder="选择要赠送的角色" style="width: 100%">
            <el-option v-for="c in characters" :key="'bc-' + c.id" :label="c.name" :value="c.id">
              <span class="option-name">{{ c.name }}</span>
              <span class="option-loc">{{ c.locationName || '尚未行动' }}</span>
            </el-option>
          </el-select>
          <p class="muted buy-tip">商品会直接放进 TA 的背包；角色只会知道「收到来自异世界的礼物」，不会知道是谁送的。</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="buyVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="buying"
          :disabled="!buyCharacterId || !buyItem || buyItem.stock <= 0"
          @click="confirmBuy"
        >
          确认赠送（{{ buyItem ? pointsOfItem(buyItem.price) : 0 }} 积分）
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import LocationIcon from '@/components/sandbox/LocationIcon.vue'
import SandboxMapViewer from '@/components/sandbox/SandboxMapViewer.vue'
import { useMemberStore } from '@/store/member'
import { emojiForItem, rarityMeta } from '@/utils/sandboxItems'
import { bbox, labelPoint, locationOfCharacter, polygonOf } from '@/utils/sandboxGeo'
import {
  portalSandbox,
  portalSandboxWorlds,
  buySandboxShopItem,
  portalSandboxActs,
  portalSandboxCoin,
  portalSandboxInteractions,
  portalSandboxWhisper
} from '@/api/sandbox'

const router = useRouter()
const route = useRoute()
const memberStore = useMemberStore()
const { userInfo } = storeToRefs(memberStore)
const isLogin = computed(() => !!userInfo.value)
const myPoints = computed(() => (userInfo.value ? userInfo.value.points : 0))

const world = reactive({ name: '', description: '', mapImage: '' })
const locations = ref([])
const characters = ref([])
const enabled = ref(true)
/** 旅人低语总开关：默认「不可见」，等接口确认开启后才显示（关闭时前台整块不出现） */
const whisperEnabled = ref(false)
/** 旅人集市 */
const shopEnabled = ref(false)
const shopTitle = ref('')
const shopItems = ref([])
const buyVisible = ref(false)
const buyItem = ref(null)
const buyCharacterId = ref(null)
const buying = ref(false)
/** 前台可切换的世界（只含「前台可见」的，可能不止一个） */
const worlds = ref([])
/** 当前世界：优先用地址栏 ?world=xxx，其次用上次选择的 */
const currentWorldId = ref(null)
const currentWorldStopped = computed(() => {
  const hit = worlds.value.find((item) => item.id === currentWorldId.value)
  return !!hit && hit.enabled !== 1
})
/** 标题里显示的世界名（当前选中的那个） */
const currentWorldName = computed(() => {
  const hit = worlds.value.find((item) => item.id === currentWorldId.value)
  return (hit && hit.name) || world.name || '沙盒世界'
})
const whisperPoints = ref(1)
const coinRate = ref(1)
const shopBuyPerDay = ref(2)
/** 地图宽度（km）：后台→世界与地图里配置，用来把坐标差换算成"约 N km" */
const kmMapWidth = ref(200)

// 商品金币价折算成积分（与后端一致：按汇率向上取整，1 金币不能算成 0 积分）
function pointsOfItem(coinPrice) {
  const rate = Math.max(1, Number(coinRate.value) || 1)
  return Math.ceil((Number(coinPrice) || 0) / rate)
}
/** 旅人纪闻（当天世界大事） */
const news = ref([])
const newsTitle = ref('')
const newsCollapsed = ref(false)
/** 被纪闻点中的地点会闪烁高亮 */
const flashLocationId = ref(null)

const activeId = ref(null)
const mapViewerVisible = ref(false)
/** 当前展开的地点（点地名后显示该地的角色） */
const activeLocationId = ref(null)
/** 背包里被选中的物品（点击格子后展示详情） */
const selectedItem = ref(null)
const filterId = ref(null)
/** 行动记录按一级地点筛选 */
const locationFilter = ref('')
const keyword = ref('')
/** 角色档案详情是否折叠（折叠后下面的行动记录仍然保留） */
const collapsed = ref(false)

const whisperTargetId = ref(null)
const whispers = ref([])
const whisperText = ref('')
const sending = ref(false)
const contributePoints = ref(5)
const contributing = ref(false)
/** 当前时间（每 30 秒刷新一次，用于「下次行动」倒计时） */
const now = ref(Date.now())
let clockTimer = null

const timeline = ref([])
const timelinePage = ref(1)
const timelineTotal = ref(0)
/** 行动记录每页条数：5 条一页在手机与桌面都刚好 */
const pageSize = 5
/** 「最新动态」用的"全世界最新几条行动"（默认 3 条，不随列表筛选变化） */
const latestActs = ref([])
/** 角色档案面板，用于「查看 TA 的行动 / 全部记录」滚动定位 */
const profileRef = ref(null)

const active = computed(() => {
  if (!activeId.value) return null
  return characters.value.find((c) => c.id === activeId.value) || null
})
/** 背包物品总件数 */
const totalItemCount = computed(() => {
  const items = (active.value && active.value.items) || []
  return items.reduce((sum, item) => sum + (Number(item.quantity) || 1), 0)
})

// 切换角色时清掉上一件选中的物品
watch(activeId, () => {
  selectedItem.value = null
})

/** 当前展开的地点（同一时间只展开一个） */
const activeLocation = computed(() => locations.value.find((loc) => loc.id === activeLocationId.value) || null)

/**
 * 地点区域的判定与标注位置都走公共工具（与后端 SandboxGeo 同一套算法），
 * 这样「角色算在哪个区域」和「地图上显示在哪个区域」永远一致。
 */
function locationPolygon(location) {
  return polygonOf(location)
}

/** 多边形顶点转成 SVG points 字符串（坐标就是 0~100 的百分比，直接对应 viewBox） */
function polygonPoints(location) {
  const polygon = polygonOf(location)
  return polygon ? polygon.map(([x, y]) => `${x},${y}`).join(' ') : ''
}

/** 地点名标签位置：区域标注点（形心，凹多边形退回内部点） */
function areaStyle(location) {
  const polygon = polygonOf(location)
  const point = polygon
    ? labelPoint(polygon)
    : [Number(location.x == null ? 50 : location.x), Number(location.y == null ? 50 : location.y)]
  return { left: point[0] + '%', top: point[1] + '%' }
}

/** 区域外接矩形，用于摆放展开出来的角色头像行 */
function locationBox(location) {
  const polygon = polygonOf(location)
  if (polygon) {
    return bbox(polygon)
  }
  const x = Number(location.x == null ? 50 : location.x)
  const y = Number(location.y == null ? 50 : location.y)
  return [x - 3, y - 3, x + 3, y + 3]
}

/** 角色属于哪个地点：先按角色记录的地点名匹配，再用坐标落在哪个区域里兜底 */
function locationOf(character) {
  return locationOfCharacter(character, locations.value)
}

function charactersIn(location) {
  if (!location) {
    return []
  }
  return characters.value.filter((character) => {
    const hit = locationOf(character)
    return hit && hit.id === location.id
  })
}

function toggleLocation(location) {
  activeLocationId.value = activeLocationId.value === location.id ? null : location.id
}

// ============================== 实际距离（km） ==============================
// 与后端 SandboxGeo 保持一致：地图宽 kmMapWidth 对应横向 100 个坐标单位，
// 地图容器是 16:9，所以纵向 1 单位的实际长度只有横向的 9/16。
const Y_UNIT_RATIO = 9 / 16

function pointOf(character) {
  return {
    x: Number(character.x == null ? 50 : character.x),
    y: Number(character.y == null ? 50 : character.y)
  }
}

function kmBetweenPoints(a, b) {
  const perUnit = (Number(kmMapWidth.value) || 200) / 100
  const dx = (a.x - b.x) * perUnit
  const dy = (a.y - b.y) * perUnit * Y_UNIT_RATIO
  return Math.sqrt(dx * dx + dy * dy)
}

function formatKm(km) {
  return km < 10 ? km.toFixed(1) + ' km' : Math.round(km) + ' km'
}

/** 算距离以谁为起点：优先时间线筛选的角色，其次正在看档案的角色，最后取世界里的第一个角色 */
const distanceBase = computed(() => {
  if (filterId.value) {
    const hit = characters.value.find((character) => character.id === filterId.value)
    if (hit) {
      return hit
    }
  }
  if (active.value) {
    return active.value
  }
  return characters.value[0] || null
})

/** 地点危险度文案：与后端 dangerText 保持一致（0 安全 / 1 较低 / 2 较高 / 3 危险） */
function dangerLabel(level) {
  const names = ['安全', '较低', '较高', '危险']
  const value = level == null ? 1 : Number(level)
  return names[value] || '较低'
}

/** 地点标注点（多边形取形心，与地图上的地名位置一致） */
function locationLabelPoint(location) {
  const polygon = polygonOf(location)
  if (polygon) {
    return labelPoint(polygon)
  }
  return [Number(location.x == null ? 50 : location.x), Number(location.y == null ? 50 : location.y)]
}

/** 地图上 hover 地点时的提示：地点描述 + 距基准角色多远（玩家能看懂 AI 为什么走这么久） */
function locationTitle(location) {
  // 危险度放在最前面：用户 hover 地点时能马上知道这里安不安全
  const base = `危险度：${dangerLabel(location.dangerLevel)}｜${location.description || location.name}`
  const from = distanceBase.value
  if (!from || !location) {
    return base
  }
  if (from.locationName && from.locationName === location.name) {
    return `${base}｜${from.name} 正在这里`
  }
  const point = locationLabelPoint(location)
  const km = kmBetweenPoints(pointOf(from), { x: point[0], y: point[1] })
  return `${base}｜距 ${from.name} 约 ${formatKm(km)}`
}

/**
 * 当前角色到指定角色的距离文本（用在"与其他角色的关系"里）。
 * 不再用"同一片区域"这种说法：区域最大能有 60 多公里，同区域也可能离得很远。
 * 只有一级地点与二级地点都相同（真的站在原地同一处）才显示"同在此处"。
 */
function distanceToCharacter(targetId) {
  const from = active.value
  const to = characters.value.find((character) => character.id === targetId)
  if (!from || !to) {
    return ''
  }
  if (sameSpot(from, to)) {
    return '同在此处'
  }
  const km = kmBetweenPoints(pointOf(from), pointOf(to))
  return km < 1 ? '就在附近' : '相距约 ' + formatKm(km)
}

/** 是否在同一个"具体地点"：一级地点与二级地点都相同（与后端 nearbyCompanions 的判定一致） */
function sameSpot(a, b) {
  return Boolean(a && b && a.locationName && a.subLocation
    && a.locationName === b.locationName && a.subLocation === b.subLocation)
}

/**
 * 这一步的移动距离（km）：时间线是从新到旧，所以往前找同一个角色更早的那条行动来对比。
 * 只在换了地区时才显示，避免"在城里走了两步"也刷一堆距离标签。
 */
function moveKmOf(act) {
  // 服务端已经算好了（分页后"上一条"可能不在这页里），优先用它
  if (act.moveKm != null) {
    return act.moveKm
  }
  const index = timeline.value.findIndex((item) => item.id === act.id)
  if (index < 0) {
    return 0
  }
  let previous = null
  for (let i = index + 1; i < timeline.value.length; i++) {
    if (timeline.value[i].characterId === act.characterId) {
      previous = timeline.value[i]
      break
    }
  }
  if (!previous || !act.locationName || act.locationName === previous.locationName) {
    return 0
  }
  return kmBetweenPoints(pointOf(act), pointOf(previous))
}

/** 点纪闻条目：如果事件发生在某个已知地点，就把那个地点展开 */
function focusNews(item) {
  const location = locations.value.find((loc) => loc.name === item.locationName)
  if (location) {
    activeLocationId.value = location.id
    // 闪烁高亮一下事件发生地，便于一眼找到
    flashLocationId.value = location.id
    setTimeout(() => {
      if (flashLocationId.value === location.id) {
        flashLocationId.value = null
      }
    }, 1700)
  }
}

/** 今天日期（纪闻卡片右上角） */
const todayText = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1}月${now.getDate()}日`
})

async function selectFromLocation(character) {
  await selectCharacter(character)
}

/** 角色头像行的位置：默认贴在区域下方，靠下时改到区域上方 */
const actorsStyle = computed(() => {
  const location = activeLocation.value
  if (!location) {
    return {}
  }
  // 用区域外接矩形来摆位置：多边形区域也能算出一个合理的贴边位置
  const box = locationBox(location)
  const below = box[3] < 68
  return {
    left: Math.min(72, Math.max(0, box[0] - 2)) + '%',
    top: (below ? box[3] : box[1]) + '%',
    transform: below ? 'translateY(8px)' : 'translateY(calc(-100% - 8px))'
  }
})

/** 角色搜索：匹配名字、称号、当前位置、外貌 */
const filteredCharacters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return characters.value
  return characters.value.filter((c) => {
    return [c.name, c.title, c.locationName, c.appearance]
      .filter(Boolean)
      .some((text) => String(text).toLowerCase().includes(kw))
  })
})

const whisperTargetName = computed(() => {
  const hit = characters.value.find((c) => c.id === whisperTargetId.value)
  return hit ? hit.name : '旅人'
})

function characterName(id) {
  const hit = characters.value.find((c) => c.id === id)
  return hit ? hit.name : `角色#${id}`
}

/** 地点显示：一级地点 · 二级地点 */
function placeText(locationName, subLocation) {
  if (!locationName && !subLocation) {
    return '某处'
  }
  return subLocation ? `${locationName || '某处'} · ${subLocation}` : locationName
}

/** 角色当前位置（都没有时显示「尚未行动」） */
function characterPlace(character) {
  if (!character) return '尚未行动'
  if (!character.locationName && !character.subLocation) return '尚未行动'
  return placeText(character.locationName, character.subLocation)
}

// ============================== 「正在前往某地」的判断 ==============================
//
// 语义：一条行动代表「从 createTime 到 createTime + nextAfterMinutes」的这段时间，
// 位置/坐标是这段时间**结束时**所在地。所以当角色还在这一步的时段内、并且这一步换了地点时，
// 角色档案里显示「正在前往 X（预计 HH:mm 抵达）」，避免"人还没到、看起来却已经在那儿"。
//
// 注意：行动记录列表里**不**显示时段（仍按生成时刻显示）——管理员点「立即执行」会让下一条提前发生，
// 用时段表示会重叠，反而更乱。

/** 解析后端时间字符串（"2026-09-17 21:08:52"） */
function parseActTime(text) {
  if (!text) return null
  const normalized = String(text).replace(' ', 'T')
  const date = new Date(normalized)
  return isNaN(date.getTime()) ? null : date
}

/** 只显示时:分 */
function clockText(date) {
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

/** 这一步是否还在进行中（还没到 next_after_minutes 的结束时间） */
function actOngoing(act) {
  const start = parseActTime(act && act.createTime)
  const minutes = Number(act && act.nextAfterMinutes) || 0
  if (!start || minutes <= 0) {
    return false
  }
  return start.getTime() + minutes * 60000 > Date.now()
}

/** 正在赶路时返回目的地与预计抵达时间；否则 null（用角色最近的行动判断） */
function travelingInfo(character) {
  const acts = (character && character.recentActs) || []
  if (acts.length === 0) {
    return null
  }
  const latest = acts[0]
  if (!actOngoing(latest)) {
    return null
  }
  // 只有"这一步换了地点"才叫赶路（日常在原地活动不算）
  const previous = acts[1]
  if (!previous || !latest.locationName || previous.locationName === latest.locationName) {
    return null
  }
  const start = parseActTime(latest.createTime)
  const arrive = start ? new Date(start.getTime() + (Number(latest.nextAfterMinutes) || 0) * 60000) : null
  return {
    location: latest.locationName,
    subLocation: latest.subLocation,
    arriveAt: arrive ? clockText(arrive) : ''
  }
}

/** 当前选中角色是否正在赶路（模板里用 computed，避免重复计算） */
const activeTravel = computed(() => travelingInfo(active.value))

/**
 * 初始化世界列表与当前世界。
 * 优先级：地址栏 ?world=xxx（可分享/刷新保持）→ 上次选择的（本地存）→ 第一个可见的世界。
 */
async function initWorlds() {
  try {
    worlds.value = (await portalSandboxWorlds()) || []
  } catch (e) {
    worlds.value = []
  }
  if (!worlds.value.length) {
    currentWorldId.value = null
    return
  }
  const fromUrl = Number(route.query.world)
  const fromStore = Number(localStorage.getItem('bcblog-sandbox-world'))
  const candidate = Number.isFinite(fromUrl) && fromUrl > 0 ? fromUrl
    : (Number.isFinite(fromStore) && fromStore > 0 ? fromStore : null)
  const hit = worlds.value.find((item) => item.id === candidate)
  currentWorldId.value = hit ? hit.id : worlds.value[0].id
  localStorage.setItem('bcblog-sandbox-world', String(currentWorldId.value))
}

/** 切换世界：重新加载地图、角色、纪闻与时间线，并把选择写进地址栏 */
async function onSwitchWorld(id) {
  currentWorldId.value = id
  localStorage.setItem('bcblog-sandbox-world', String(id))
  router.replace({ query: { ...route.query, world: id } })
  // 换了世界，角色与地点筛选要清掉，否则会筛不到东西
  filterId.value = null
  activeId.value = null
  whisperTargetId.value = null
  locationFilter.value = ''
  activeLocationId.value = null
  await load()
  await loadTimeline(true)
}


async function load() {
  const data = await portalSandbox(currentWorldId.value || undefined)
  Object.assign(world, data.world || {})
  locations.value = data.locations || []
  characters.value = data.characters || []
  enabled.value = !!data.enabled
  // 旅人低语总开关：关闭时前台整块隐藏
  whisperEnabled.value = data.whisperEnabled !== false
  // 旅人集市：后台总开关关闭时整块不显示
  shopEnabled.value = data.shopEnabled === true
  shopTitle.value = data.shopTitle || '旅人集市'
  shopItems.value = data.shopItems || []
  whisperPoints.value = data.whisperPoints == null ? 1 : data.whisperPoints
  coinRate.value = data.coinRate == null ? 1 : data.coinRate
  shopBuyPerDay.value = data.shopBuyPerDay == null ? 2 : data.shopBuyPerDay
  kmMapWidth.value = data.kmMapWidth == null ? 200 : data.kmMapWidth
  news.value = data.news || []
  newsTitle.value = data.newsTitle || ''
  if (characters.value.length) {
    await selectCharacter(characters.value[0])
  }
  await loadTimeline(true)
  await loadLatestActs()
}

async function selectCharacter(character) {
  activeId.value = character.id
  filterId.value = character.id
  whisperTargetId.value = character.id
  // 同时刷新角色数据，保证好感度等数值是最新的
  await Promise.all([loadWhispers(), loadTimeline(true), refreshCharacters()])
}

/**
 * 切到「全部」：隐藏角色详情，只按"全部角色"看行动记录。
 * 保留当前地点筛选（用户可能正想看"某个地方所有人的动静"）。
 */
async function selectAllCharacters() {
  activeId.value = null
  filterId.value = null
  await loadTimeline(true)
}

/** 最新动态卡片上的「查看 TA 的行动」：选中该角色并滚到档案区 */
async function viewCharacterActs(characterId) {
  const character = characters.value.find((item) => item.id === characterId)
  if (!character) {
    return
  }
  scrollToProfile()
  await selectCharacter(character)
}

function scrollToProfile() {
  if (profileRef.value && profileRef.value.scrollIntoView) {
    profileRef.value.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

function openMapViewer() {
  if (!world.mapImage) {
    ElMessage.warning('管理员还没有上传地图背景')
    return
  }
  mapViewerVisible.value = true
}

/** 在放大的地图里点角色：选中后关掉全屏，回到页面看档案 */
async function onViewerSelect(character) {
  await selectCharacter(character)
  mapViewerVisible.value = false
}

async function loadWhispers() {
  if (!whisperTargetId.value) {
    whispers.value = []
    return
  }
  try {
    const data = await portalSandboxInteractions({ characterId: whisperTargetId.value, page: 1, size: 20 })
    whispers.value = (data.list || []).slice().reverse()
  } catch (e) {
    whispers.value = []
  }
}

async function loadTimeline(reset = false) {
  if (reset) {
    timelinePage.value = 1
  }
  const data = await portalSandboxActs({
    characterId: filterId.value || undefined,
    locationName: locationFilter.value || undefined,
    worldId: currentWorldId.value || undefined,
    page: timelinePage.value,
    size: pageSize
  })
  timelineTotal.value = data.total || 0
  // 页码分页：每次都整体替换。以前这里是"加载更多"式的累加，
  // 改成页码翻页后如果还累加，翻回第 1 页会变成 1+2+1 三页叠在一起（看起来像"全部记录"）
  timeline.value = data.list || []
  // 行动被清理或数量变少时当前页可能越界，直接退回第 1 页，避免停在空白页
  if (!timeline.value.length && timelinePage.value > 1) {
    timelinePage.value = 1
    await loadTimeline(false)
  }
}

/** 页码翻页：直接跳到第 page 页（不再用"加载更多"累加） */
async function onTimelinePage(page) {
  timelinePage.value = page
  await loadTimeline(false)
  scrollToProfile()
}

/** 「最新动态」：取全世界最新的 3 条行动（不随列表筛选变化） */
async function loadLatestActs() {
  try {
    const data = await portalSandboxActs({
      worldId: currentWorldId.value || undefined,
      page: 1,
      size: 3
    })
    latestActs.value = data.list || []
  } catch (e) {
    latestActs.value = []
  }
}

async function sendWhisper() {
  if (!isLogin.value) {
    ElMessage.warning('登录后才能留下低语')
    router.push('/portal/login')
    return
  }
  if (!whisperTargetId.value) {
    ElMessage.warning('请先选择要互动的角色')
    return
  }
  const content = whisperText.value.trim()
  if (!content) {
    ElMessage.warning('说点什么再发送吧')
    return
  }
  sending.value = true
  try {
    await portalSandboxWhisper({ characterId: whisperTargetId.value, content })
    whisperText.value = ''
    ElMessage.success(`低语已送给「${whisperTargetName.value}」`)
    await loadWhispers()
    await memberStore.fetchInfo(true)
  } finally {
    sending.value = false
  }
}

/** 打开商品的赠送弹窗 */
/** 库存条宽度：剩余 / 总量 */
function stockPercent(item) {
  const total = Number(item.totalStock || 0)
  const stock = Number(item.stock || 0)
  if (total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((stock / total) * 100)))
}

function openBuy(item) {
  if (!isLogin.value) {
    ElMessage.warning('登录后才能把商品送给角色')
    return
  }
  if (!item || item.stock <= 0) {
    ElMessage.warning('这件商品已经售罄了')
    return
  }
  buyItem.value = item
  // 默认选中当前正在看的角色，省一步操作
  buyCharacterId.value = active.value ? active.value.id : (characters.value[0] ? characters.value[0].id : null)
  buyVisible.value = true
}

/** 确认赠送：扣积分 → 商品进角色背包 → 刷新商品与角色数据 */
async function confirmBuy() {
  if (!buyItem.value || !buyCharacterId.value) {
    return
  }
  buying.value = true
  try {
    await buySandboxShopItem({ itemId: buyItem.value.id, characterId: buyCharacterId.value })
    const name = (characters.value.find((c) => c.id === buyCharacterId.value) || {}).name || '角色'
    ElMessage.success(`已经把「${buyItem.value.name}」送给${name}`)
    buyVisible.value = false
    await refreshCharacters()
    if (active.value) {
      await selectCharacter(characters.value.find((c) => c.id === active.value.id) || characters.value[0])
    }
    await memberStore.fetchInfo(true)
  } finally {
    buying.value = false
  }
}

/** 只刷新地图与角色数据，保留当前选中的角色 */
async function refreshCharacters() {
  const data = await portalSandbox(currentWorldId.value || undefined)
  Object.assign(world, data.world || {})
  locations.value = data.locations || []
  characters.value = data.characters || []
  enabled.value = !!data.enabled
  whisperPoints.value = data.whisperPoints == null ? 1 : data.whisperPoints
  coinRate.value = data.coinRate == null ? 10 : data.coinRate
  news.value = data.news || []
  newsTitle.value = data.newsTitle || ''
}

function coinTypeText(type) {
  if (type === 'contribute') return '旅人贡献'
  if (type === 'earn') return '赚取'
  if (type === 'spend') return '花销'
  if (type === 'shop_buy') return '集市购物'
  if (type === 'init') return '初始金币'
  if (type === 'admin') return '管理员调整'
  return '其他变动'
}

/** 好感度 -100~100 映射到进度条宽度 */
function favorPercent(favor) {
  const value = Number(favor || 0)
  return Math.max(0, Math.min(100, (value + 100) / 2))
}

function favorColor(favor) {
  const value = Number(favor || 0)
  if (value < 0) return '#e2664f'
  if (value < 40) return '#b9a5c9'
  if (value < 80) return '#7bc47f'
  return '#f2b23e'
}

function parseTime(text) {
  if (!text) return null
  const date = new Date(String(text).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

/** 前台展示下一次行动时间：优先显示相对时间，方便判断角色什么时候会动 */
function nextRunText(character) {
  if (!character) return '—'
  if (!enabled.value) return '沙盒已暂停'
  if (character.enabled !== 1) return '该角色已停用自动行动'
  const next = parseTime(character.nextRunTime)
  if (!next) return '待安排'
  const hhmm = String(character.nextRunTime).slice(11, 16)
  const minutes = Math.round((next.getTime() - now.value) / 60000)
  // 带上 AI 给出的原因，例如「正在睡觉，约 6 小时后（07:00）」
  const reason = character.nextReason ? `正在${character.nextReason}，` : ''
  if (minutes <= 0) return `${reason}即将行动（${hhmm}）`
  if (minutes < 60) return `${reason}约 ${minutes} 分钟后（${hhmm}）`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest
    ? `${reason}约 ${hours} 小时 ${rest} 分钟后（${hhmm}）`
    : `${reason}约 ${hours} 小时后（${hhmm}）`
}

async function onContribute() {
  if (!isLogin.value) {
    ElMessage.warning('登录后才能贡献金币')
    router.push('/portal/login')
    return
  }
  if (!activeId.value) {
    ElMessage.warning('请先选择一个角色')
    return
  }
  const points = Number(contributePoints.value)
  if (!Number.isFinite(points) || points < 1) {
    ElMessage.warning('贡献积分至少要 1')
    return
  }
  contributing.value = true
  try {
    const res = await portalSandboxCoin({ characterId: activeId.value, points })
    ElMessage.success(`已为「${active.value ? active.value.name : '角色'}」贡献 ${res.gained} 金币`)
    await memberStore.fetchInfo(true)
    await refreshCharacters()
  } finally {
    contributing.value = false
  }
}

onMounted(async () => {
  // 手机上默认收起纪闻卡片，避免挡住地图
  newsCollapsed.value = window.innerWidth < 720
  await initWorlds()
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, 30000)
  try {
    if (memberStore.token) {
      await memberStore.fetchInfo()
    }
    await load()
  } catch (e) {
    // 加载失败时保留空状态
  }
})

onBeforeUnmount(() => {
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
})
</script>

<style scoped>
.sandbox {
  max-width: 1320px;
  margin: 0 auto;
  padding: calc(var(--header-height) + 32px) 20px 60px;
}
.sandbox-head { text-align: center; margin-bottom: 22px; }
.head-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}
/*
 * 世界标题：本身就是切换入口（按钮 + 下拉菜单），比塞一个 select 干净得多。
 * 平时就是标题的样子，悬停时浮出一层浅色底 + 底部渐变小横线，暗示可以点。
 */
.world-title-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 14px;
  border: none;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  transition: background 0.25s ease;
}
.world-title-btn:hover { background: rgba(255, 255, 255, 0.42); }
.world-title-name {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 2px;
  line-height: 1.35;
  color: var(--text-strong);
  position: relative;
}
/* 标题下的小渐变线，跟着主题色走 */
.world-title-name::after {
  content: '';
  position: absolute;
  left: 2px;
  right: 2px;
  bottom: -2px;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, transparent, var(--accent-2, var(--accent)), transparent);
  opacity: 0.55;
}
.world-title-caret {
  flex-shrink: 0;
  margin-top: 4px;
  color: var(--text-muted);
  transition: transform 0.25s ease;
}
.world-title-btn:hover .world-title-caret { transform: translateY(2px); }
/* 下拉菜单：世界名在左、状态标签在右，当前世界加粗高亮 */
.world-menu :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 180px;
  font-size: 14px;
}
.world-menu :deep(.el-dropdown-menu__item.is-current) {
  color: var(--accent-2, var(--accent));
  font-weight: 600;
}
.world-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.world-option-name { overflow: hidden; text-overflow: ellipsis; }
.world-option-tag {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  color: #c98a2a;
  background: rgba(233, 186, 80, 0.16);
}
.sandbox-head h1 {
  margin: 0 0 8px;
  font-size: 30px;
  letter-spacing: 2px;
  color: var(--text-strong);
}
.sandbox-head p { margin: 0; color: var(--text-muted); font-size: 14px; }
.paused {
  display: inline-block;
  margin-top: 12px;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 12px;
  color: #b07a20;
  background: rgba(255, 200, 90, 0.18);
  border: 1px solid rgba(255, 200, 90, 0.35);
}

.card, .map-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  backdrop-filter: blur(12px);
  margin-bottom: 22px;
}
.map-card { padding: 14px; }
.map-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--accent-soft);
}
.map-bg { width: 100%; height: 100%; object-fit: cover; display: block; }
.map-empty {
  padding: 70px 20px;
  text-align: center;
  color: var(--text-muted);
}
.map-empty .sub { font-size: 12px; opacity: 0.8; }

/* 地点区域：半透明色块 + 虚线边框，默认只显示图标与地名 */
/* 旅人纪闻：地图左上角的悬浮卡片 */
.news-card {
  position: absolute;
  left: 12px;
  top: 12px;
  z-index: 5;
  width: 230px;
  max-width: 46%;
  padding: 8px 10px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.46);
  backdrop-filter: blur(7px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.28);
  color: #fff;
}
.news-head {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}
.news-name {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
}
.news-date { font-size: 11px; opacity: 0.7; }
.news-toggle { margin-left: auto; font-size: 11px; opacity: 0.75; }
.news-list {
  margin-top: 6px;
  max-height: 160px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.news-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 0;
  border: none;
  background: transparent;
  color: #fff;
  text-align: left;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.5;
}
.news-item:hover .news-text { color: var(--accent); }
.news-level {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  margin-top: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.55);
}
.news-level.lv-2 { background: #f2b23e; box-shadow: 0 0 8px rgba(242, 178, 62, 0.8); }
.news-level.lv-3 { background: #ff6f6f; box-shadow: 0 0 9px rgba(255, 111, 111, 0.9); }
.news-text { flex: 1; }
.news-place {
  flex-shrink: 0;
  font-size: 10px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  white-space: nowrap;
}
.news-card.collapsed { width: auto; }

/* 区域形状层：viewBox 就是 0~100 的百分比坐标系，所以 SVG 坐标可以直接当地图坐标用 */
.area-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 3;
  overflow: visible;
}
.area-shape {
  fill: rgba(255, 255, 255, 0.16);
  stroke: rgba(255, 255, 255, 0.75);
  stroke-width: 1.5;
  stroke-dasharray: 4 3;
  /* 地图被拉伸时线宽保持不变，否则细长地图的边框会粗细不一 */
  vector-effect: non-scaling-stroke;
  cursor: pointer;
  transition: fill 0.25s ease, stroke 0.25s ease;
}
/* 悬停：明显加深（未悬停时保持原来的 0.16 不变），并描边变实、加一点投影，避免看不出来 */
.area-shape:hover {
  fill: rgba(255, 255, 255, 0.46);
  stroke-width: 2.4;
  stroke-dasharray: none;
  filter: drop-shadow(0 0 5px rgba(0, 0, 0, 0.35));
}
.area-shape.on {
  fill: var(--accent-soft);
  stroke: var(--accent);
  stroke-width: 2;
  stroke-dasharray: none;
}
.area-shape.point { fill: rgba(255, 255, 255, 0.42); }
.area-shape.flash {
  stroke: #ff6f9f;
  stroke-width: 2.4;
  stroke-dasharray: none;
  animation: areaFlash 0.8s ease-in-out 2;
}

/* 区域名标签：放在区域标注点上，本身不占区域面积 */
.area {
  position: absolute;
  z-index: 4;
  transform: translate(-50%, -50%);
  pointer-events: none;
}
.area-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.42);
  white-space: nowrap;
  pointer-events: auto;
  cursor: pointer;
  transition: background 0.25s ease, box-shadow 0.25s ease;
}
.area-label:hover { background: rgba(0, 0, 0, 0.6); }
.area.on .area-label {
  background: linear-gradient(120deg, var(--accent), var(--accent-2));
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.35), 0 6px 18px rgba(0, 0, 0, 0.3);
}
.area-name { overflow: hidden; text-overflow: ellipsis; }
.area-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  color: #fff;
  background: rgba(0, 0, 0, 0.34);
}

/* 点击地点后展开的角色头像行 */
.area-actors {
  position: absolute;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  max-width: 72%;
  padding: 6px 8px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(6px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.28);
}
.area-actor {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}
.area-actor-img {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.9);
  background: var(--card-solid);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  color: var(--text-strong);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.area-actor:hover .area-actor-img { transform: translateY(-2px); }
.area-actor.on .area-actor-img { box-shadow: 0 0 0 3px var(--accent); }
.area-actor-name { font-size: 11px; color: #fff; white-space: nowrap; }
.area-empty { font-size: 12px; color: rgba(255, 255, 255, 0.75); }
.map-zoom-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 4;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  backdrop-filter: blur(6px);
}
.map-zoom-btn:hover { background: rgba(0, 0, 0, 0.6); }

.panel { padding: 18px 22px 22px; }
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.panel-head h3 { margin: 0; font-size: 15px; color: var(--text-strong); }
.panel-tools { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.panel-body { margin-top: 14px; }
.char-search {
  width: 220px;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card-solid);
  color: var(--text);
  font-size: 13px;
  outline: none;
}
.char-search:focus { border-color: var(--accent); }
.ghost-btn {
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
}
.ghost-btn:hover { color: var(--accent); border-color: var(--accent); }

.char-chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding-bottom: 4px;
  margin-bottom: 8px;
}
.char-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px 6px 6px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--glass-bg);
  color: var(--text);
  cursor: pointer;
  text-align: left;
}
.char-chip.on { border-color: var(--accent); background: var(--accent-soft); }
.chip-avatar { width: 30px; height: 30px; border-radius: 50%; object-fit: cover; }
.chip-avatar.fallback {
  display: flex; align-items: center; justify-content: center;
  background: var(--accent-soft); color: var(--text-strong); font-size: 13px;
}
.chip-text { display: flex; flex-direction: column; line-height: 1.25; }
.chip-name { font-size: 13px; color: var(--text-strong); }
.chip-loc { font-size: 11px; color: var(--text-muted); }

.detail { margin-top: 10px; }
.detail-head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.detail-avatar { width: 74px; height: 74px; border-radius: 18px; object-fit: cover; }
.detail-title h2 { margin: 0; font-size: 20px; color: var(--text-strong); }
.detail-title .title { margin: 3px 0 0; color: var(--accent); font-size: 13px; }
.muted { color: var(--text-muted); font-size: 13px; margin: 4px 0 0; }
.time { color: var(--text-muted); font-size: 12px; margin-left: 8px; }
.status { margin-left: auto; display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-strong);
  background: var(--accent-soft);
  border: 1px solid var(--border);
}
.appearance { margin: 16px 0 0; color: var(--text); font-size: 14px; line-height: 1.9; }
.act-body { margin-top: 6px; white-space: pre-line; line-height: 1.8; color: var(--text); font-size: 14px; }
.voice { margin-top: 6px; color: var(--accent-2); font-size: 13px; font-style: italic; }

.coin-chip {
  background: rgba(233, 186, 80, 0.22);
  border-color: rgba(233, 186, 80, 0.45);
  color: #d9a441;
  font-weight: 600;
}
.contribute {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: var(--glass-bg);
  border: 1px solid var(--border);
}
.contribute-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.contribute-head strong { font-size: 14px; color: var(--text-strong); }
.contribute-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.num-input {
  width: 84px;
  padding: 7px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card-solid);
  color: var(--text);
  font-size: 13px;
  text-align: center;
}
.coin-history { margin-top: 16px; }
.coin-history h3 { margin: 0 0 8px; font-size: 15px; color: var(--text-strong); }
.coin-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 13px;
}
.coin-type { color: var(--text-muted); min-width: 66px; }
.coin-delta { font-weight: 600; }
.coin-delta.plus { color: #4caf62; }
.coin-delta.minus { color: #e2664f; }
.coin-delta.inline { margin-left: 10px; font-size: 12px; }
.coin-remark { color: var(--text); flex: 1; }
.companion-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #c98a2a;
  background: rgba(233, 186, 80, 0.18);
  border: 1px solid rgba(233, 186, 80, 0.4);
  white-space: nowrap;
}
.companion-tag.inline { margin-left: 10px; }
.move-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #6f9f8a;
  background: rgba(111, 159, 138, 0.16);
  border: 1px solid rgba(111, 159, 138, 0.38);
  white-space: nowrap;
}
.move-tag.inline { margin-left: 8px; }
.favor-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #c95a82;
  background: rgba(255, 111, 159, 0.16);
  border: 1px solid rgba(255, 111, 159, 0.38);
  white-space: nowrap;
}
.favor-tag.inline { margin-left: 8px; }
.react-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #7f9bd1;
  background: rgba(127, 155, 209, 0.18);
  border: 1px solid rgba(127, 155, 209, 0.4);
  white-space: nowrap;
}
.react-tag.inline { margin-left: 8px; }
.item-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #4f9d8f;
  background: rgba(79, 157, 143, 0.16);
  border: 1px solid rgba(79, 157, 143, 0.38);
  white-space: nowrap;
}
.item-tag.inline { margin-left: 8px; }
.news-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #c98a2a;
  background: rgba(233, 186, 80, 0.16);
  border: 1px solid rgba(233, 186, 80, 0.4);
  white-space: nowrap;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  vertical-align: bottom;
}
.news-tag.inline { margin-left: 8px; }
.area.flash .area-label {
  background: linear-gradient(120deg, #ff6f9f, #ff9ec4);
  animation: areaFlash 0.8s ease-in-out 2;
}
@keyframes areaFlash {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 111, 159, 0); }
  50% { box-shadow: 0 0 0 7px rgba(255, 111, 159, 0.55); }
}
/* 背包：二次元游戏风格的物品格子 */
.backpack { margin-top: 18px; }
.backpack h3, .memories h3 { margin: 0 0 10px; font-size: 15px; color: var(--text-strong); }
.backpack-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; }
.backpack-head h3 { margin-bottom: 10px; }
.backpack-meta { font-size: 12px; color: var(--text-muted); }
.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(78px, 1fr));
  gap: 10px;
}
.item-slot {
  position: relative;
  aspect-ratio: 1 / 1.12;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 4px;
  border-radius: 14px;
  border: 1.5px solid var(--border);
  background: var(--glass-bg);
  color: var(--text-strong);
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.item-slot::before {
  /* 斜向高光，模仿游戏道具格的质感 */
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(140deg, rgba(255, 255, 255, 0.22), transparent 55%);
  pointer-events: none;
}
.item-slot:hover { transform: translateY(-3px); }
.item-slot.on { transform: translateY(-3px); }
.slot-icon {
  font-size: 26px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
}
.slot-icon img { width: 34px; height: 34px; object-fit: contain; }
.slot-name {
  max-width: 100%;
  font-size: 11px;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.slot-qty {
  position: absolute;
  right: 5px;
  bottom: 5px;
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}
.backpack-empty {
  padding: 18px;
  border-radius: var(--radius-sm);
  border: 1px dashed var(--border);
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}
.item-detail {
  display: flex;
  gap: 14px;
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  border: 1.5px solid var(--border);
  background: var(--glass-bg);
}
.detail-item-icon {
  font-size: 34px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  flex-shrink: 0;
}
.detail-item-icon img { width: 44px; height: 44px; object-fit: contain; }
.detail-main { flex: 1; min-width: 0; }
.detail-name { font-size: 15px; color: var(--text-strong); display: flex; align-items: center; gap: 8px; }
.rarity-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
}
.detail-qty { margin-top: 4px; font-size: 12px; color: var(--text-muted); }
.detail-desc { margin-top: 6px; font-size: 13px; color: var(--text); line-height: 1.8; }

/* 品质配色（1 普通 → 5 传说） */
.rarity-1 { border-color: rgba(185, 179, 201, 0.45); background: rgba(185, 179, 201, 0.14); }
.rarity-1 .rarity-tag { color: #9c95ae; background: rgba(185, 179, 201, 0.22); }
.rarity-2 { border-color: rgba(99, 192, 122, 0.55); background: rgba(99, 192, 122, 0.15); box-shadow: 0 4px 14px rgba(99, 192, 122, 0.16); }
.rarity-2 .rarity-tag { color: #63c07a; background: rgba(99, 192, 122, 0.22); }
.rarity-3 { border-color: rgba(91, 155, 213, 0.6); background: rgba(91, 155, 213, 0.16); box-shadow: 0 4px 16px rgba(91, 155, 213, 0.2); }
.rarity-3 .rarity-tag { color: #5b9bd5; background: rgba(91, 155, 213, 0.22); }
.rarity-4 { border-color: rgba(168, 117, 224, 0.65); background: rgba(168, 117, 224, 0.18); box-shadow: 0 4px 18px rgba(168, 117, 224, 0.24); }
.rarity-4 .rarity-tag { color: #a875e0; background: rgba(168, 117, 224, 0.24); }
.rarity-5 { border-color: rgba(240, 177, 60, 0.7); background: rgba(240, 177, 60, 0.2); box-shadow: 0 4px 20px rgba(240, 177, 60, 0.28); }
.rarity-5 .rarity-tag { color: #f0b13c; background: rgba(240, 177, 60, 0.26); }
.rarity-5 .slot-icon, .item-detail.rarity-5 .detail-item-icon { text-shadow: 0 0 12px rgba(240, 177, 60, 0.7); }
.memories { margin-top: 18px; }
.memory-item {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--glass-bg);
  border: 1px solid var(--border);
  margin-bottom: 10px;
}
.memory-date { font-size: 12px; color: var(--accent); margin-bottom: 4px; }
.memory-text { font-size: 13.5px; color: var(--text); line-height: 1.85; white-space: pre-line; }
.relations { margin-top: 18px; }
.relations h3 { margin: 0 0 10px; font-size: 15px; color: var(--text-strong); }
.relation-item {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--glass-bg);
  border: 1px solid var(--border);
  margin-bottom: 10px;
}
.relation-avatar { width: 38px; height: 38px; border-radius: 12px; object-fit: cover; flex-shrink: 0; }

/* ===== 想法（对实力/财富的看法 + 最近几次变化） ===== */
.attitude { margin: 14px 0; }
.attitude h3 { margin: 0 0 8px; font-size: 14px; }
.attitude-views { display: flex; flex-wrap: wrap; gap: 8px; }
.attitude-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  font-size: 12.5px;
}
.attitude-chip .label { color: var(--el-text-color-secondary); font-size: 11.5px; }
.attitude-history { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.attitude-item { display: flex; align-items: flex-start; gap: 8px; font-size: 12.5px; }
.attitude-tag { flex: 0 0 auto; padding: 1px 7px; border-radius: 8px; font-size: 11.5px; }
.attitude-tag.power { color: #b4553f; background: rgba(180, 85, 63, 0.14); }
.attitude-tag.wealth { color: #b08d2a; background: rgba(176, 141, 42, 0.16); }
.attitude-body { flex: 1 1 auto; min-width: 0; }
.attitude-change { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.attitude-change .old { color: var(--el-text-color-secondary); text-decoration: line-through; }
.attitude-change .arrow { color: var(--el-text-color-secondary); }
.attitude-change .new { font-weight: 600; }
.attitude-reason { margin-top: 2px; color: var(--el-text-color-secondary); }
.attitude-reason .major { margin-left: 6px; color: #c0392b; }
.attitude-item .time { flex: 0 0 auto; font-size: 11.5px; color: var(--el-text-color-secondary); }
.relation-avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-soft);
  color: var(--text-strong);
  font-size: 15px;
}
.relation-main { flex: 1; min-width: 0; }
.relation-name { font-size: 14px; color: var(--text-strong); }
.relation-name .muted { margin-left: 8px; font-size: 12px; }
.relation-bar { display: flex; align-items: center; gap: 10px; margin-top: 6px; }
.relation-track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--accent-soft);
  overflow: hidden;
}
.relation-fill { height: 100%; border-radius: 999px; transition: width 0.6s ease; }
.relation-value { font-size: 12px; color: var(--text-muted); white-space: nowrap; }
.favor-delta {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  white-space: nowrap;
}
.favor-delta.plus { color: #4caf62; background: rgba(76, 175, 98, 0.16); }
.favor-delta.minus { color: #e2664f; background: rgba(226, 102, 79, 0.16); }
.relation-reverse, .relation-remark { margin-top: 4px; font-size: 12px; color: var(--text-muted); }

.target-label { font-size: 13px; color: var(--text-muted); }
.target-select {
  width: 200px;
}
.target-select :deep(.el-select__wrapper) {
  border-radius: 999px;
  font-size: 13px;
}
.option-name { margin-right: 10px; }
.option-loc { color: var(--text-muted); font-size: 12px; }

.whisper-list { margin: 12px 0; }
.whisper-item { display: flex; gap: 10px; padding: 10px 0; border-bottom: 1px dashed var(--border); }
.whisper-avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.whisper-avatar.fallback {
  display: flex; align-items: center; justify-content: center;
  background: var(--accent-soft); color: var(--text-strong); font-size: 13px;
}
.whisper-user { font-size: 13px; color: var(--text-strong); }
.whisper-text { font-size: 14px; color: var(--text); line-height: 1.7; margin-top: 2px; }
.whisper-form { display: flex; gap: 10px; align-items: flex-end; }
.whisper-form textarea {
  flex: 1;
  min-height: 68px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--card-solid);
  color: var(--text);
  font-family: inherit;
  font-size: 14px;
  resize: vertical;
}
.send, .more {
  padding: 10px 18px;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(120deg, var(--accent), var(--accent-2));
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.send:disabled { opacity: 0.6; cursor: not-allowed; }

/* 行动记录（原来的「行动时间线」合并进角色档案后的样式） */
.acts { margin-top: 22px; padding-top: 18px; border-top: 1px dashed var(--border); }
.acts-head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
.acts-head h3 { margin: 0; font-size: 15px; color: var(--text-strong); }
.acts-tools { display: flex; align-items: center; gap: 10px; margin-left: auto; }
.acts-total { font-size: 12px; }
.acts-pager { margin-top: 16px; justify-content: center; }
.panel-hint { padding: 6px 0 2px; }
/* 地图与角色档案之间的最新动态：最近 3 条，各自一个容器，纵向排三行 */
.latest-block { margin-bottom: 22px; }
.latest-block-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.latest-label {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--accent);
  background: rgba(255, 111, 159, 0.14);
  border: 1px solid rgba(255, 111, 159, 0.32);
}
/* 三行纵向排列：每张卡片独占一行 */
.latest-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 10px; }
.latest-card {
  padding: 12px 16px;
  /* 卡片之间由 gap 控制间距，去掉 .card 默认的下边距 */
  margin-bottom: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.latest-time { flex-shrink: 0; color: var(--text-muted); font-size: 12px; }
.latest-text {
  flex: 1;
  min-width: 0;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.latest-btn { flex-shrink: 0; }
.collapse-btn { flex-shrink: 0; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; }
.loc-filter { width: 150px; }
.loc-filter :deep(.el-select__wrapper) { border-radius: 999px; font-size: 12px; }
.chip-btn {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
}
.chip-btn.on { background: var(--accent-soft); color: var(--accent); border-color: transparent; }
.timeline-item { display: flex; gap: 14px; padding: 14px 0; border-bottom: 1px dashed var(--border); }
.timeline-time { width: 150px; flex-shrink: 0; color: var(--text-muted); font-size: 12px; padding-top: 3px; }
.timeline-content { flex: 1; }
.timeline-title { font-size: 14px; color: var(--text-strong); }
.timeline-title .muted { margin-left: 8px; font-size: 12px; }
@media (max-width: 720px) {
  .sandbox { padding: calc(var(--header-height) + 20px) 12px 50px; }
  .detail-head { align-items: flex-start; }
  .status { margin-left: 0; width: 100%; }
  .timeline-item { flex-direction: column; gap: 4px; }
  /* 窄屏：最新动态的卡片内改成竖排，按钮不再和文字抢宽度 */
  .latest-card { flex-wrap: wrap; }
  .latest-text { white-space: normal; }
  .latest-btn { margin-left: 0; }
  .loc-filter { width: 130px; }
  .timeline-time { width: auto; }
  .area-label { font-size: 11px; padding: 1px 6px; }
  .area-actors { max-width: 88%; gap: 8px; }
  .area-actor-img { width: 36px; height: 36px; font-size: 15px; }
  .whisper-form { flex-direction: column; align-items: stretch; }
  .char-search { width: 150px; }
  .panel { padding: 16px 14px 18px; }
}
/* 战斗力：展示在角色信息里，行动变化时给个标签 */
.combat-chip { color: #b0416b; background: rgba(255, 111, 159, 0.16); }
.goal-chip { color: #4f9d8f; background: rgba(79, 157, 143, 0.16); }
.combat-tag {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #b0416b;
  background: rgba(255, 111, 159, 0.16);
  border: 1px solid rgba(255, 111, 159, 0.34);
  white-space: nowrap;
}
.combat-tag.inline { margin-left: 8px; }
/* 集市卡片自己带内边距：标题、副标题与商品格子都不再贴着容器边框 */
.shop { padding: 22px 26px 26px; }
/* ============ 旅人集市：摊位感卡片（品质辉光 + 图标底座 + 库存条 + 售罄印章） ============ */
.shop-head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.shop-head h3 {
  margin: 0;
  font-size: 16px;
  letter-spacing: 1px;
  background: linear-gradient(90deg, var(--accent), var(--accent-2, var(--accent)));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.shop-head h3::before { content: '✦ '; color: var(--accent); -webkit-text-fill-color: var(--accent); }
.shop-empty { padding: 26px 0; text-align: center; }
.shop-grid {
  display: grid;
  /* 电脑端一排最多 5 个 */
  grid-template-columns: repeat(5, minmax(0, 1fr));
  /* 卡片之间的间隔 */
  gap: 18px;
  padding: 2px 0 4px;
}
/* 窗口变窄时逐级减少每行数量，保证卡片不会挤在一起 */
@media (max-width: 1280px) {
  .shop-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 1000px) {
  .shop-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
.shop-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  /* 卡片整体收小一圈，显得更精致 */
  padding: 13px 10px 10px;
  border: 1.5px solid var(--r-border, var(--border));
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0.16));
  cursor: pointer;
  overflow: hidden;
  text-align: center;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}
/* 顶部品质绸带 */
.shop-ribbon {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, transparent, var(--r-color, var(--accent)), transparent);
}
/* 品质角标 */
.shop-rarity-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 10px;
  letter-spacing: 0.5px;
  color: #fff;
  background: var(--r-color, var(--accent));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
}
/* 图标底座：像摊位上的展台 */
.shop-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: radial-gradient(circle at 32% 28%, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.35));
  box-shadow: inset 0 -3px 8px rgba(0, 0, 0, 0.06), 0 6px 14px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--r-border, var(--border));
  transition: transform 0.25s ease;
 }
.shop-icon { font-size: 22px; line-height: 1; }
.shop-icon.big { font-size: 40px; }
.shop-name { font-size: 13px; font-weight: 600; color: var(--r-color, var(--text-strong)); }
.shop-desc {
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 30px;
}
.shop-price { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 700; color: #c98a2a; }
.coin-dot { font-size: 11px; }
/* 库存条：一眼看出还剩多少 */
.shop-stockbar {
  width: 84%;
  height: 4px;
  margin-top: 2px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.08);
  overflow: hidden;
}
.shop-stockbar i {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--r-color, var(--accent)), var(--accent-2, var(--accent)));
  transition: width 0.3s ease;
}
.shop-stock { font-size: 11px; color: var(--text-muted); }
.shop-stock.out { color: #d9534f; font-weight: 600; }
/* 售罄印章 */
.shop-stamp {
  position: absolute;
  top: 46%;
  right: -30px;
  padding: 3px 30px;
  transform: rotate(-16deg);
  font-size: 12px;
  letter-spacing: 2px;
  color: #fff;
  background: rgba(217, 83, 79, 0.9);
  box-shadow: 0 4px 12px rgba(217, 83, 79, 0.35);
}
.shop-cell:hover {
  transform: translateY(-4px);
  border-color: var(--r-color, var(--accent));
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.16), 0 0 0 1px var(--r-border, transparent);
}
.shop-cell:hover .shop-icon-wrap { transform: translateY(-2px) scale(1.06); }
/* 高品质：加一层柔光，越稀有越亮 */
.shop-cell.r4 { box-shadow: 0 6px 18px rgba(168, 117, 224, 0.18); }
.shop-cell.r5 { box-shadow: 0 6px 20px rgba(240, 177, 60, 0.24); }
.shop-cell.soldout { filter: grayscale(0.65); opacity: 0.72; }
.shop-cell.soldout:hover { transform: none; box-shadow: none; }
/* 赠送弹窗 */
.buy-body { display: flex; flex-direction: column; gap: 16px; }
.buy-top { display: flex; gap: 14px; }
.buy-info { flex: 1; }
.buy-name { font-size: 16px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.buy-rarity { font-size: 11px; padding: 0 6px; border-radius: 999px; background: rgba(0, 0, 0, 0.06); color: var(--text-muted); }
.buy-desc { margin: 6px 0; color: var(--text-muted); font-size: 13px; line-height: 1.7; }
.buy-price { display: flex; gap: 12px; font-size: 13px; }
.buy-block-title { font-size: 13px; font-weight: 600; margin-bottom: 6px; color: var(--text-strong); }
.buy-orders { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-muted); }
.buy-order { padding: 2px 0; }
.buy-tip { margin: 8px 0 0; font-size: 12px; line-height: 1.6; }
@media (max-width: 720px) {
  /* 手机端保持原样：两列 + 原来的间距 */
  .shop { padding: 16px 14px 18px; }
  .shop-grid { grid-template-columns: repeat(2, 1fr); gap: 14px; padding: 0; }
}</style>
