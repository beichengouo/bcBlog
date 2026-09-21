<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>沙盒角色</span>
        <div class="toolbar-right">
          <span class="tip">世界</span>
          <el-select v-model="selectedWorldId" size="small" style="width: 150px" @change="onSwitchWorld">
            <el-option
              v-for="item in worlds"
              :key="item.id"
              :label="item.name || ('世界 ' + item.id)"
              :value="item.id"
            >
              <div class="world-option">
                <span class="world-option-name">{{ item.name || ('世界 ' + item.id) }}</span>
                <span v-if="item.enabled !== 1" class="world-option-tag">已停止</span>
              </div>
            </el-option>
          </el-select>
          <span class="tip">共 {{ list.length }} 个角色</span>
          <el-button type="warning" plain :loading="runningAll" @click="onRunAll">
            {{ runningAll && runAllProgressText ? `全员行动中 ${runAllProgressText}` : '全员行动一轮' }}
          </el-button>
          <el-button plain :loading="repairing" @click="onRepairCoins">金币对账</el-button>
          <el-button type="primary" @click="openAdd">新增角色</el-button>
        </div>
      </div>
    </template>

    <el-alert
      class="tips"
      type="info"
      :closable="false"
      title="角色的人设写法参考酒馆角色卡：写清身份、性格、口癖、目标与禁忌，AI 才会稳定地扮演。绑定服务商与模型后，角色会按设定的间隔自动在地图上活动。"
    />

    <el-table :data="list" v-loading="loading">
      <el-table-column label="立绘" width="80">
        <template #default="{ row }">
          <img v-if="row.avatar" class="avatar-thumb" :src="row.avatar" alt="" />
          <div v-else class="avatar-empty">{{ (row.name || '?').slice(0, 1) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="140">
        <template #default="{ row }">
          <div class="char-name">{{ row.name }}</div>
          <div class="char-title">{{ row.title || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="当前位置" min-width="150">
        <template #default="{ row }">
          <div>{{ row.locationName || '尚未行动' }}</div>
          <div class="char-title">x={{ row.x }} , y={{ row.y }}</div>
        </template>
      </el-table-column>
      <el-table-column label="AI 服务商 / 模型" min-width="180">
        <template #default="{ row }">
          <div>{{ providerName(row.providerId) }}</div>
          <div class="char-title">{{ row.model || '未选择模型' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="自动行动" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled === 1"
            @change="(val) => onToggleEnabled(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="下次行动" width="170">
        <template #default="{ row }">
          <span>{{ row.enabled === 1 ? (row.nextRunTime || '待计算') : '已停用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="战斗力" width="90">
        <template #default="{ row }">
          <span class="combat-cell">{{ row.combatPower == null ? 10 : row.combatPower }}</span>
        </template>
      </el-table-column>
      <el-table-column label="当前目标" min-width="140">
        <template #default="{ row }">
          <span class="goal-cell">{{ row.goal || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="金币" width="90">
        <template #default="{ row }">
          <span class="coin-cell">{{ row.coins || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最近状态" min-width="160">
        <template #default="{ row }">
          <el-tag v-if="row.lastError" type="danger" size="small">{{ row.lastError }}</el-tag>
          <span v-else class="char-title">正常</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210">
        <template #default="{ row }">
          <el-button
            size="small"
            type="primary"
            :loading="isRunning(row.id)"
            :disabled="isRunning(row.id)"
            @click="onRun(row)"
          >
            立即执行
          </el-button>
          <el-button size="small" @click="openBackpack(row)">背包</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
          <!-- 行动卡住（AI 超时/进程重启）时手动解围；正常执行完锁会自动释放 -->
          <el-button v-if="row.runningAt" size="small" type="warning" plain @click="onUnlock(row)">
            解除执行锁
          </el-button>
          <!-- 执行完成后留一个时间戳：扫一眼就知道哪个角色这次跑过、哪个没跑 -->
          <div v-if="lastRunAt[row.id]" class="run-stamp">最近执行 · {{ lastRunAt[row.id] }}</div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 最近执行结果：单角色执行与「全员行动一轮」都写到这里，避免只看得到一闪而过的提示 -->
    <el-card class="mt run-results" shadow="never">
      <template #header>
        <div class="toolbar">
          <span>
            最近执行结果
            <span class="tip">（保留最近 {{ RUN_RESULT_LIMIT }} 条，刷新页面后清空）</span>
          </span>
          <div class="toolbar-right">
            <el-button size="small" @click="resultsCollapsed = !resultsCollapsed">
              {{ resultsCollapsed ? '展开' : '收起' }}
            </el-button>
            <el-button size="small" :disabled="!runResults.length" @click="runResults = []">清空</el-button>
          </div>
        </div>
      </template>
      <div v-show="!resultsCollapsed">
        <div v-if="!runResults.length" class="tip">
          还没有执行记录：点表格里的「立即执行」或右上角「全员行动一轮」，结果会显示在这里。
        </div>
        <div v-for="item in runResults" :key="item.id" class="run-item" :class="{ failed: !item.ok }">
          <div class="run-head">
            <el-tag size="small" :type="item.ok ? 'success' : 'danger'">{{ item.ok ? '成功' : '失败' }}</el-tag>
            <strong>{{ item.characterName }}</strong>
            <span class="tip">{{ item.time }}</span>
            <span v-if="item.ok && item.act && item.act.locationName" class="tip">
              在 {{ placeText(item.act) }}
            </span>
            <el-button size="small" text type="primary" @click="viewInActLog(item)">在行动日志里查看</el-button>
          </div>
          <template v-if="item.ok && item.act">
            <div class="run-text">{{ item.act.actions }}</div>
            <div v-if="item.act.innerVoice" class="run-voice">「{{ item.act.innerVoice }}」</div>
            <div class="run-tags">
              <span v-if="item.act.moveKm" class="run-tag">移动 {{ formatKm(item.act.moveKm) }}</span>
              <span v-if="item.act.coinChange" class="run-tag">
                金币 {{ item.act.coinChange > 0 ? '+' : '' }}{{ item.act.coinChange }}
              </span>
              <span v-if="item.act.combatChange" class="run-tag">
                战斗力 {{ item.act.combatChange > 0 ? '+' : '' }}{{ item.act.combatChange }}
              </span>
              <span v-if="item.act.itemChange" class="run-tag">{{ item.act.itemChange }}</span>
              <span v-if="item.act.companions" class="run-tag">与 {{ item.act.companions }} 互动</span>
              <span v-if="item.act.favorChange" class="run-tag">好感 {{ item.act.favorChange }}</span>
              <span v-if="item.act.nextAfterReason" class="run-tag">
                下次：{{ item.act.nextAfterReason }}（{{ item.act.nextAfterMinutes || 0 }} 分钟后）
              </span>
            </div>
          </template>
          <div v-else class="run-error">{{ item.error || '执行失败' }}</div>
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="backpackVisible"
      :title="`背包 · ${backpackCharacter ? backpackCharacter.name : ''}`"
      width="min(96vw, 860px)"
    >
      <p class="tip" style="margin: 0 0 10px">
        装备栏 4 格：武器 / 副手 / 护具 / 饰品，一格一件。装备加成计入战斗力，但<b>不能超过角色的自身实力</b>
        （服务端会拦下来）；破损的装备会被自动卸下、加成归零，修好后再装备。
      </p>
      <el-table :data="equipmentRows" size="small" class="equip-table">
        <el-table-column label="槽位" width="90">
          <template #default="{ row }">
            <span class="equip-slot-name">{{ row.label }}</span>
          </template>
        </el-table-column>
        <el-table-column label="装备" min-width="180">
          <template #default="{ row }">
            <div class="equip-cell">
              <span class="item-icon-cell small">
                <img v-if="row.item && row.item.icon" :src="row.item.icon" alt="" />
                <template v-else>{{ row.item ? itemEmoji(row.item) : '—' }}</template>
              </span>
              <span :class="{ 'muted-text': !row.item }">{{ row.item ? row.item.name : '空着' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="战斗力加成" width="110">
          <template #default="{ row }">
            <span v-if="row.item" class="bonus-chip">+{{ row.item.powerBonus || 0 }}</span>
            <span v-else class="muted-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <template v-if="row.item">
              <el-button size="small" @click="onUnequip(row.item)">卸下</el-button>
              <el-button v-if="row.item.broken" size="small" type="warning" @click="onRepairItem(row.item)">
                修复
              </el-button>
            </template>
            <span v-else class="muted-text">从下面「背包」里点「装备」</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="equip-summary">
        自身实力 {{ backpackCharacter ? (backpackCharacter.combatPower || 10) : 10 }}
        <span class="sep">+</span>
        装备加成 {{ backpackEquipPower }}
        <span class="sep">=</span>
        <b>战斗力 {{ backpackTotalPower }}</b>
        <el-button size="small" text type="primary" style="margin-left: 10px" @click="onRefreshEquipPower">
          重算加成
        </el-button>
      </div>

      <el-divider content-position="left">背包</el-divider>
      <el-table :data="backpackItems" v-loading="loadingItems" size="small">
        <el-table-column label="图标" width="90">
          <template #default="{ row }">
            <el-upload
              :action="'/api/admin/upload/image'"
              :headers="uploadHeaders"
              :show-file-list="false"
              accept="image/*"
              :on-success="(res) => onItemIconSuccess(row, res)"
              :on-error="onUploadError"
            >
              <span class="item-icon-cell" :title="row.icon ? '点击更换图标' : '点击上传图标'">
                <img v-if="row.icon" :src="row.icon" alt="" />
                <template v-else>{{ itemEmoji(row) }}</template>
              </span>
            </el-upload>
          </template>
        </el-table-column>
        <el-table-column label="物品" min-width="190">
          <template #default="{ row }">
            <div class="item-name-cell">
              <span>{{ row.name }}</span>
              <span
                v-if="isEquipItem(row)"
                class="item-equip-tag"
                :class="{ broken: row.broken === 1 }"
              >
                {{ equipBadgeText(row) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :max="9999" size="small" @change="onUpdateItem(row)" />
          </template>
        </el-table-column>
        <el-table-column label="品质" width="120">
          <template #default="{ row }">
            <el-select v-model="row.rarity" size="small" @change="onUpdateItem(row)">
              <el-option v-for="r in ITEM_RARITIES" :key="r.value" :label="r.name" :value="r.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="170" show-overflow-tooltip />
        <el-table-column label="装备" width="200">
          <template #default="{ row }">
            <el-select
              v-model="row.slot"
              size="small"
              style="width: 92px"
              @change="onUpdateItem(row)"
            >
              <el-option v-for="s in SLOT_OPTIONS" :key="s.key" :label="s.label" :value="s.key" />
            </el-select>
            <el-input-number
              v-model="row.powerBonus"
              :min="0"
              :max="999"
              size="small"
              style="width: 74px; margin-left: 6px"
              :disabled="!row.slot || row.slot === 'none'"
              @change="onUpdateItem(row)"
            />
            <el-button
              v-if="row.slot && row.slot !== 'none'"
              size="small"
              style="margin-left: 6px"
              @click="row.equipped ? onUnequip(row) : onEquip(row)"
            >
              {{ row.equipped ? '卸下' : '装备' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="onDeleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingItems && !backpackItems.length" description="背包是空的" :image-size="60" />

      <div class="item-add">
        <el-input v-model="newItem.name" placeholder="物品名称" style="width: 150px" maxlength="100" />
        <el-input-number v-model="newItem.quantity" :min="1" :max="9999" />
        <el-input v-model="newItem.description" placeholder="说明（可选）" style="width: 190px" maxlength="300" />
        <el-button type="primary" :loading="savingItem" @click="onAddItem">添加物品</el-button>
      </div>
      <p class="tip">
        AI 行动时会读取背包内容（提示词里已强调），并可能通过 items_change 增减物品；这里可以随时手动补充或修正。
      </p>
      <template #footer>
        <el-button @click="backpackVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="min(94vw, 680px)">
      <el-form :model="form" label-width="100px">
        <template v-if="!form.id">
          <el-form-item label="AI 一键创作">
            <div class="ai-box">
              <div class="ai-row">
                <el-select v-model="aiProviderId" placeholder="选择服务商" style="width: 190px" @change="onAiProviderChange">
                  <el-option v-for="p in providers" :key="'aip-' + p.id" :label="p.name" :value="p.id" />
                </el-select>
                <el-button :loading="aiModelLoading" @click="loadAiModels">获取模型</el-button>
                <el-select
                  v-model="aiModel"
                  filterable
                  allow-create
                  placeholder="选择模型"
                  style="width: 210px"
                >
                  <el-option v-for="m in aiModels" :key="m" :label="m" :value="m" />
                </el-select>
              </div>
              <el-input
                v-model="aiRequirement"
                type="textarea"
                :rows="3"
                maxlength="300"
                show-word-limit
                placeholder="描述你想要的角色，例如：一个在白鸦村卖花的少女，怕生但话多，随身带着一把旧口琴"
              />
              <div class="ai-row">
                <el-button type="primary" plain :loading="aiGenerating" @click="onAiGenerate">
                  AI 生成并填充
                </el-button>
                <span class="tip">会按当前世界观、地图地点和已有角色生成，填充后你还可以逐项修改</span>
              </div>
              <div v-if="draftPlace" class="draft-place">初始地点：{{ draftPlace }}</div>
              <div v-if="draftItems.length" class="draft-items">
                <span class="draft-title">初始物品：</span>
                <span v-for="(item, index) in draftItems" :key="'di-' + index" class="draft-item">
                  {{ itemEmoji(item) }} {{ item.name }} ×{{ item.quantity }}
                  <em>{{ rarityMeta(item.rarity).name }}</em>
                  <em v-if="isEquipItem(item)" class="draft-equip" :title="equipBadgeText(item)">
                    {{ slotEmoji(item.slot) }}{{ (item.powerBonus || 0) > 0 ? '战斗力 +' + item.powerBonus : '装备' }}
                  </em>
                  <button type="button" title="移除这件初始物品" @click="draftItems.splice(index, 1)">×</button>
                </span>
              </div>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="角色名">
          <el-input v-model="form.name" placeholder="如：魔女零" maxlength="100" />
        </el-form-item>
        <el-form-item label="称号">
          <el-input v-model="form.title" placeholder="如：流浪的星辉魔女" maxlength="100" />
        </el-form-item>
        <el-form-item label="头像 / 立绘">
          <el-upload
            :action="'/api/admin/upload/image'"
            :headers="uploadHeaders"
            :show-file-list="false"
            accept="image/*"
            :on-success="onAvatarSuccess"
            :on-error="onUploadError"
          >
            <img v-if="form.avatar" class="avatar-preview" :src="form.avatar" alt="" />
            <el-button v-else>上传图片</el-button>
          </el-upload>
          <el-button v-if="form.avatar" link type="danger" @click="form.avatar = ''">清除</el-button>
        </el-form-item>
        <el-form-item label="外貌描述">
          <el-input
            v-model="form.appearance"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="如：银白色长发，戴着一顶过大的黑色尖帽，披风上缀着星辉"
          />
          <span class="tip">这是不会变的「底子」：种族、发色、瞳色、身形与标志性特征</span>
        </el-form-item>
        <el-form-item label="此刻的模样">
          <el-input
            v-model="form.currentLook"
            maxlength="200"
            style="width: 420px"
            placeholder="如：斗篷上还沾着夜路的泥点，长发用旧布条松松束着"
          />
          <span class="tip">会随行动变化：穿着、脏污、伤势外观、发型神态；AI 每步自动更新，也可以在这里手工改</span>
        </el-form-item>
        <el-form-item label="角色人设">
          <el-input
            v-model="form.persona"
            type="textarea"
            :rows="10"
            placeholder="参考酒馆角色卡写法：身份 / 性格 / 说话方式 / 目标 / 禁忌&#10;例如：零是活了三百年的魔女，外表少女，说话懒散但心思细腻；喜欢收集星辉碎片；讨厌被人称呼为「大人」；不会主动伤害任何人。"
          />
          <div class="tip">
            <el-button link type="primary" @click="fillSample">填入示例模板（魔女零，可自行修改）</el-button>
          </div>
        </el-form-item>
        <el-form-item label="AI 服务商">
          <el-select v-model="form.providerId" placeholder="选择服务商" style="width: 220px" @change="onProviderChange">
            <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button :loading="modelLoading" @click="loadModels">获取模型列表</el-button>
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="form.model" filterable allow-create placeholder="选择或输入模型名" style="width: 300px">
            <el-option v-for="m in models" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="采样温度">
          <el-slider v-model="temperature" :min="0" :max="2" :step="0.05" style="width: 260px" />
          <span class="tip">{{ temperature }}</span>
        </el-form-item>
        <el-form-item label="初始坐标">
          <el-input-number v-model="form.x" :min="0" :max="100" @change="onCoordinateChange" />
          <span class="range-sep">,</span>
          <el-input-number v-model="form.y" :min="0" :max="100" @change="onCoordinateChange" />
          <span class="tip">x 横向、y 纵向，0~100 的地图百分比；改坐标会自动把「一级地点」同步成坐标所在的地区</span>
        </el-form-item>
        <el-form-item label="一级地点">
          <el-select
            v-model="form.locationName"
            clearable
            placeholder="按坐标自动匹配"
            style="width: 220px"
            @change="onLocationPicked"
          >
            <el-option v-for="loc in locations" :key="loc.id" :label="loc.name" :value="loc.name" />
          </el-select>
          <span class="tip">选一个地区会把坐标落到该地区内部；坐标和地点始终一致，地图与距离才不会打架</span>
        </el-form-item>
        <el-form-item label="二级地点">
          <el-input
            v-model="form.subLocation"
            maxlength="90"
            style="width: 320px"
            placeholder="例如：协会门外的喷泉长椅（换了一级地点会自动清空）"
          />
        </el-form-item>
        <el-form-item label="免遭遇地点">
          <el-select
            v-model="exemptLocations"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="这些地点不会遭遇袭击（可留空）"
            style="width: 340px"
          >
            <el-option v-for="loc in locations" :key="'ex-' + loc.id" :label="loc.name" :value="loc.name" />
          </el-select>
          <span class="tip">
            选中的<b>一级地点</b>里，这个角色不会触发遭遇（整个地区的任何二级地点都算）。
            适合"魔王待在自己的魔王城""商人待在自己商会所在的城市"这类设定；留空 = 正常参与遭遇判定
          </span>
        </el-form-item>
        <el-form-item label="下次行动">
          <el-date-picker
            v-model="form.nextRunTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空则按间隔自动安排"
            style="width: 215px"
          />
          <el-input
            v-model="form.nextReason"
            maxlength="40"
            style="width: 170px; margin-left: 8px"
            placeholder="原因，如 睡觉"
          />
          <span class="tip">删除行动日志后想手工回退角色状态时用：这里可以直接安排下一次行动的时间</span>
        </el-form-item>
        <el-form-item label="行动间隔">
          <el-input-number v-model="form.intervalMin" :min="1" :max="1440" />
          <span class="range-sep">~</span>
          <el-input-number v-model="form.intervalMax" :min="1" :max="1440" />
          <span class="tip">分钟，建议 45~75；这是「AI 没给间隔」时使用的随机区间</span>
        </el-form-item>
        <el-form-item label="AI 间隔上下限">
          <el-input-number v-model="form.aiIntervalMin" :min="1" :max="10080" controls-position="right" placeholder="全局" />
          <span class="range-sep">~</span>
          <el-input-number v-model="form.aiIntervalMax" :min="1" :max="10080" controls-position="right" placeholder="全局" />
          <span class="tip">分钟。留空则用全局设置（默认 15~720）；例如给爱睡觉的角色放宽到 600，避免频繁被叫醒</span>
        </el-form-item>
        <el-form-item label="金币余额">
          <el-input-number v-model="form.coins" :min="0" :max="99999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="战斗力">
          <el-input-number v-model="form.combatPower" :min="1" :max="9999" controls-position="right" />
          <span class="tip">
            综合实力（战斗技巧<template v-if="manaLabel">、{{ manaLabel }}</template>、装备），默认 10；
            AI 在行动里遇到学会新魔法、得到强力装备、受伤这类事件时也会自己微调
          </span>
          <span class="tip">角色身上的钱：AI 日常活动会赚取或消耗，前台用户也能用积分贡献</span>
        </el-form-item>
        <el-form-item label="当前目标">
          <el-input
            v-model="form.goal"
            maxlength="100"
            style="width: 320px"
            placeholder="例如：去晨雾森林采药（AI 会自己维护，你也可以直接改）"
          />
          <span class="tip">目标不同的角色会各自行动，不容易一直黏在一起</span>
        </el-form-item>
        <el-form-item label="对实力的看法">
          <el-input
            v-model="form.powerView"
            maxlength="60"
            style="width: 320px"
            placeholder="例如：不甘平庸，想变强"
          />
          <span class="tip">写进行动提示词：想变强的角色会主动修炼、拜师、攒钱买装备</span>
        </el-form-item>
        <el-form-item label="对财富的看法">
          <el-input
            v-model="form.wealthView"
            maxlength="60"
            style="width: 320px"
            placeholder="例如：穷怕了，拼命攒钱 / 钱是身外之物"
          />
          <span class="tip">写进行动提示词：看重钱的角色会多接委托、摆摊做买卖，不在意的就散财、安稳过日子</span>
        </el-form-item>
        <el-form-item label="当前状态">
          <div class="status-editor">
            <div class="status-row">
              <span class="status-label">体力</span>
              <el-input-number v-model="statusForm.体力" :min="0" :max="100" controls-position="right" />
              <template v-if="manaLabel">
                <span class="status-label">{{ manaLabel }}</span>
                <el-input-number v-model="statusForm[manaLabel]" :min="0" :max="100" controls-position="right" />
              </template>
            </div>
            <div class="status-row">
              <span class="status-label">饥饿度</span>
              <el-input-number v-model="statusForm.饥饿度" :min="0" :max="100" controls-position="right" />
              <span class="status-label">心情</span>
              <el-input v-model="statusForm.心情" placeholder="如：平静" maxlength="20" style="width: 130px" />
            </div>
            <div class="status-row">
              <span class="status-label">伤势</span>
              <el-select v-model="statusForm.伤势" style="width: 130px">
                <el-option v-for="level in INJURY_LEVELS" :key="level" :label="level" :value="level" />
              </el-select>
              <span class="tip">AI 行动时也会改这一项；濒死 / 重伤后会自动拉长下一次行动间隔（默认 6 小时 / 3 小时）</span>
            </div>
            <div class="status-row" v-for="(item, index) in extraStatus" :key="'extra-' + index">
              <el-input v-model="item.key" placeholder="状态名（如 精神）" style="width: 150px" maxlength="20" />
              <el-input v-model="item.value" placeholder="值（如 良好）" style="width: 150px" maxlength="30" />
              <el-button link type="danger" @click="extraStatus.splice(index, 1)">删除</el-button>
            </div>
            <el-button link type="primary" @click="extraStatus.push({ key: '', value: '' })">
              + 添加自定义状态
            </el-button>
            <div class="tip">
              体力 / {{ manaLabel || '资源条' }} / 饥饿度 用 0~100，心情等其它项可以写文字；AI 每次行动后会自动更新这些数值，这里也可以随时手改。
              <template v-if="!manaLabel">
                （当前世界没有「魔力」这条属性，可在「世界与地图 → 魔力条名称」里开启）
              </template>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="开局剧情">
          <el-checkbox v-model="generateFirstAct">保存后立即生成第一条行动</el-checkbox>
          <span class="tip">会调用一次 AI，生成角色的第一个故事并写入行动记录；不勾选则只创建角色</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  sandboxCharacters,
  sandboxWorlds,
  saveSandboxCharacter,
  deleteSandboxCharacter,
  runSandboxCharacter,
  runAllSandboxCharacters,
  sandboxRunAllProgress,
  sandboxActs,
  sandboxItems,
  repairSandboxCoins,
  unlockSandboxCharacter,
  saveSandboxItem,
  deleteSandboxItem,
  setSandboxItemEquip,
  repairSandboxItem,
  refreshSandboxEquipPower,
  generateSandboxCharacter,
  sandboxLocations
} from '@/api/sandbox'
import { aiProviderList, aiProviderModels } from '@/api/ai'
import { useSandboxWorld } from '@/composables/useSandboxWorld'
import {
  emojiForItem,
  ITEM_RARITIES,
  rarityMeta,
  EQUIP_SLOTS,
  SLOT_OPTIONS,
  isEquipItem,
  equipBadgeText,
  itemEmoji,
  slotEmoji
} from '@/utils/sandboxItems'
import { interiorPoint, locationAtPoint, polygonOf } from '@/utils/sandboxGeo'

const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }

const list = ref([])
const providers = ref([])
const models = ref([])
/** 当前世界的地区列表：编辑角色时用来按坐标反查「一级地点」 */
const locations = ref([])
/** 免遭遇地点（多选）：和 form.encounterExemptLocations 的"逗号分隔字符串"互转 */
const exemptLocations = ref([])
const loading = ref(false)
const saving = ref(false)
const modelLoading = ref(false)
/** 正在执行中的角色 ID 集合：允许多个角色同时执行，各自独立转圈 */
const runningIds = ref([])
const runningAll = ref(false)
/** 全员行动的进度文案（例如 "2/5"），后端异步执行期间显示在按钮上 */
const runAllProgressText = ref('')
/** 金币对账中 */
const repairing = ref(false)
/**
 * 「最近执行结果」面板：单角色执行与全员行动一轮的结果都写这里，最新在最上面。
 * 只存在内存里（刷新即清空），用来弥补"执行完成只有一个一闪而过的提示"。
 */
const runResults = ref([])
const resultsCollapsed = ref(false)
const RUN_RESULT_LIMIT = 8
/** 每个角色最近一次执行的完成时间（行内展示「最近执行 · 12:34」） */
const lastRunAt = reactive({})
const backpackVisible = ref(false)
const backpackCharacter = ref(null)
const backpackItems = ref([])
const loadingItems = ref(false)
const savingItem = ref(false)
const newItem = reactive({ name: '', quantity: 1, description: '' })
/** AI 一键创作相关状态 */
const aiProviderId = ref(null)
const aiModel = ref('')
const aiModels = ref([])
const aiModelLoading = ref(false)
const aiRequirement = ref('')
const aiGenerating = ref(false)
const draftItems = ref([])
const draftPlace = ref('')
/** 是否在保存后立即生成第一条行动 */
const generateFirstAct = ref(true)
const dialogVisible = ref(false)
/** 当前世界（三个沙盒页面共用一个选择） */
const worlds = ref([])
const { currentWorldId, setCurrentWorld } = useSandboxWorld()
const router = useRouter()
const selectedWorldId = ref(null)
const temperature = ref(0.9)
/**
 * 本世界「魔力」这条资源条叫什么（在「世界与地图 → 魔力条名称」里配）：
 * 剑与魔法是「魔力」、修仙是「灵力」、现代是「精力」；空串表示这个世界没有这条属性。
 * 没配过（null）时按默认「魔力」显示，避免老世界突然少一条。
 */
const manaLabel = computed(() => {
  const found = (worlds.value || []).find((item) => item.id === selectedWorldId.value)
  if (!found || found.manaLabel === null || found.manaLabel === undefined) {
    return '魔力'
  }
  return String(found.manaLabel).trim()
})
/** 标准状态项，对应 AI 提示词里的固定字段 */
// 伤势单独用下拉编辑（四档），所以从"自定义状态项"里排除，避免同一个键出现两份
function standardStatusKeys() {
  return ['体力', manaLabel.value, '饥饿度', '心情', '伤势'].filter(Boolean)
}
/** 伤势档位：与后端 SandboxDeathGuard.INJURY_LEVELS 保持一致 */
const INJURY_LEVELS = ['无恙', '轻伤', '重伤', '濒死']
const statusForm = reactive({ 体力: 100, 饥饿度: 20, 心情: '平静', 伤势: '无恙' })
const extraStatus = ref([])

/**
 * 表单默认值：新增/编辑前都先整体重置。
 * 教训：以前编辑是"手写字段清单"塞进表单，漏了「战斗力 / 当前目标」，
 * 结果编辑 A 保存后，B 的战斗力被写成了 A 的值（数据被改坏）。
 * 现在改成"先重置 → 再按字段名批量灌入这一行的值"，以后新增字段也不会再漏。
 */
const FORM_DEFAULTS = {
  id: null,
  name: '',
  title: '',
  avatar: '',
  appearance: '',
  // 此刻的样子：穿着、干净程度、伤势外观这些会随行动变化的状态（AI 每步更新，也可手工改）
  currentLook: '',
  persona: '',
  providerId: null,
  model: '',
  temperature: 0.9,
  x: 50,
  y: 50,
  locationName: '',
  subLocation: '',
  encounterExemptLocations: '',
  coins: 0,
  combatPower: 10,
  goal: '',
  powerView: '',
  wealthView: '',
  nextRunTime: null,
  nextReason: '',
  intervalMin: 45,
  intervalMax: 75,
  aiIntervalMin: null,
  aiIntervalMax: null,
  enabled: 1
}

const form = reactive({ ...FORM_DEFAULTS })

/** 把表单整体恢复成默认值（含状态项、草稿物品等附件） */
function resetForm() {
  Object.assign(form, FORM_DEFAULTS)
  statusForm.体力 = 100
  statusForm.饥饿度 = 20
  statusForm.心情 = '平静'
  statusForm.伤势 = '无恙'
  if (manaLabel.value) {
    statusForm[manaLabel.value] = 100
  }
  extraStatus.value = []
  draftItems.value = []
  draftPlace.value = ''
  models.value = []
  temperature.value = 0.9
  exemptLocations.value = []
}

async function load() {
  loading.value = true
  try {
    // 角色和地区一起拉：编辑角色时要按坐标反查所在地区（与后端同一套判定）
    const [characters, places] = await Promise.all([
      sandboxCharacters(selectedWorldId.value),
      sandboxLocations(selectedWorldId.value)
    ])
    list.value = characters || []
    locations.value = places || []
  } finally {
    loading.value = false
  }
}

/** 切换世界：与「世界与地图」「行动日志」共用同一个选择 */
async function onSwitchWorld(id) {
  setCurrentWorld(id)
  // 换了世界，执行结果与行内时间戳都属于上一个世界，清掉避免张冠李戴
  runResults.value = []
  Object.keys(lastRunAt).forEach((key) => delete lastRunAt[key])
  await load()
}

/** 首次进入：先取世界列表并定位到当前世界（与其它沙盒页面保持一致） */
async function initWorld() {
  worlds.value = (await sandboxWorlds()) || []
  if (!worlds.value.length) {
    selectedWorldId.value = null
    return
  }
  const stored = currentWorldId.value
  const exists = worlds.value.some((item) => item.id === stored)
  setCurrentWorld(exists ? stored : worlds.value[0].id)
  selectedWorldId.value = currentWorldId.value
}

async function loadProviders() {
  try {
    providers.value = await aiProviderList()
  } catch (e) {
    providers.value = []
  }
}

function providerName(id) {
  if (!id) return '未绑定'
  const hit = providers.value.find((p) => p.id === id)
  return hit ? hit.name : `服务商 #${id}`
}

function onAvatarSuccess(res) {
  if (res && res.code === 200) {
    form.avatar = res.data
  } else {
    ElMessage.error((res && res.msg) || '上传失败')
  }
}

/** 解析角色状态 JSON */
function parseStatus(json) {
  const result = {}
  if (!json) return result
  try {
    const obj = typeof json === 'string' ? JSON.parse(json) : json
    Object.keys(obj || {}).forEach((key) => {
      result[key] = obj[key]
    })
  } catch (e) {
    // 解析失败就当没有状态
  }
  return result
}

function numOr(value, fallback) {
  const num = Number(value)
  return Number.isFinite(num) ? num : fallback
}

/** 把表单里的状态拼成 JSON 字符串 */
function buildStatusJson() {
  const status = {
    体力: statusForm.体力,
    饥饿度: statusForm.饥饿度,
    // 伤势是四档固定值：AI 行动和后台编辑都走同一套白名单
    伤势: INJURY_LEVELS.includes(statusForm.伤势) ? statusForm.伤势 : '无恙'
  }
  // 资源条按本世界的叫法写（没有这条属性的世界就不写）
  if (manaLabel.value) {
    status[manaLabel.value] = numOr(statusForm[manaLabel.value], 100)
  }
  if (statusForm.心情) {
    status.心情 = statusForm.心情
  }
  extraStatus.value.forEach((item) => {
    const key = (item.key || '').trim()
    if (key) {
      status[key] = item.value
    }
  })
  return JSON.stringify(status)
}

function onUploadError() {
  ElMessage.error('图片上传失败，请检查文件大小和格式')
}

function openAdd() {
  resetForm()
  // 新建时的默认服务商：沿用列表里第一个（AI 一键创作也用它）
  form.providerId = providers.value.length ? providers.value[0].id : null
  aiProviderId.value = form.providerId || (providers.value.length ? providers.value[0].id : null)
  aiModel.value = ''
  aiModels.value = []
  aiRequirement.value = ''
  generateFirstAct.value = true
  dialogVisible.value = true
}

function openEdit(row) {
  // 先整体重置，再按字段名批量灌入这一行的值：
  // 这样"表单里有、但这行没给"的字段会回到默认值，而不会残留上一个角色的值
  resetForm()
  Object.keys(form).forEach((key) => {
    if (key !== 'id' && row[key] !== undefined) {
      form[key] = row[key]
    }
  })
  form.id = row.id
  // 几个需要归一化的字段（空值要回落到默认，而不是把 null 提交上去）
  form.name = row.name || ''
  form.title = row.title || ''
  form.avatar = row.avatar || ''
  form.appearance = row.appearance || ''
  form.currentLook = row.currentLook || ''
  form.persona = row.persona || ''
  form.model = row.model || ''
  form.temperature = Number(row.temperature || 0.9)
  form.x = row.x == null ? 50 : row.x
  form.y = row.y == null ? 50 : row.y
  form.locationName = row.locationName || ''
  form.subLocation = row.subLocation || ''
  form.coins = row.coins == null ? 0 : row.coins
  form.combatPower = row.combatPower == null ? 10 : row.combatPower
  form.goal = row.goal || ''
  form.powerView = row.powerView || ''
  form.wealthView = row.wealthView || ''
  form.encounterExemptLocations = row.encounterExemptLocations || ''
  // 逗号分隔的字符串 → 多选数组（后端存的是"地点名,地点名"）
  exemptLocations.value = form.encounterExemptLocations
    ? form.encounterExemptLocations.split(',').map((name) => name.trim()).filter(Boolean)
    : []
  form.nextRunTime = row.nextRunTime || null
  form.nextReason = row.nextReason || ''
  form.intervalMin = row.intervalMin || 45
  form.intervalMax = row.intervalMax || 75
  form.aiIntervalMin = row.aiIntervalMin == null ? null : row.aiIntervalMin
  form.aiIntervalMax = row.aiIntervalMax == null ? null : row.aiIntervalMax
  form.enabled = row.enabled == null ? 1 : row.enabled
  const status = parseStatus(row.statusJson)
  statusForm.体力 = numOr(status['体力'], 100)
  if (manaLabel.value) {
    statusForm[manaLabel.value] = numOr(status[manaLabel.value], 100)
  }
  statusForm.饥饿度 = numOr(status['饥饿度'], 20)
  statusForm.心情 = status['心情'] == null ? '' : String(status['心情'])
  const injury = status['伤势'] == null ? '无恙' : String(status['伤势'])
  statusForm.伤势 = INJURY_LEVELS.includes(injury) ? injury : '无恙'
  extraStatus.value = Object.keys(status)
    .filter((key) => !standardStatusKeys().includes(key))
    // 这个世界没有这条属性时，历史遗留的「魔力」不再当成自定义状态往外露
    .filter((key) => !(key === '魔力' && !manaLabel.value))
    .map((key) => ({ key, value: String(status[key]) }))
  temperature.value = Number(row.temperature || 0.9)
  models.value = form.model ? [form.model] : []
  // 打开时按坐标静默校正一次地点名：历史数据里可能存在"只改过坐标、地点名没跟上"的行，
  // 让表单直接显示真实归属，管理员一眼就能看出角色到底在哪
  syncPlaceByPoint(false)
  dialogVisible.value = true
}

function onAiProviderChange() {
  aiModels.value = []
  aiModel.value = ''
}

// ============================== 坐标 ↔ 一级地点 联动 ==============================
//
// 角色的位置由「一级地点名」和「坐标」两个字段共同表示：
// 地图按一级地点名归类（决定角色画在哪个区域、能不能相遇），距离按坐标计算。
// 只改其中一个就会出现矛盾，所以这里两个方向都联动，后端保存时还会再校正一次。

/**
 * 按坐标反查所在地区，把一级地点同步过去。
 * notify=true 表示是管理员手动改坐标（弹提示）；打开编辑时静默同步一次，
 * 让他在表单里看到的就是真实的归属，而不是"旧地点名 + 新坐标"的矛盾状态。
 */
function syncPlaceByPoint(notify) {
  const x = Number(form.x)
  const y = Number(form.y)
  if (!locations.value.length || Number.isNaN(x) || Number.isNaN(y)) {
    return
  }
  const hit = locationAtPoint(locations.value, x, y, false)
  if (!hit || hit.name === form.locationName) {
    return
  }
  form.locationName = hit.name
  // 二级地点是旧的一级地点里的具体小地方，换了大地点就作废
  form.subLocation = ''
  if (notify) {
    ElMessage.info(`坐标位于「${hit.name}」，已同步一级地点`)
  }
}

/** 手改坐标：按坐标反查所在地区，把一级地点同步过去（不在任何地区里就不动，交给后端兜底） */
function onCoordinateChange() {
  syncPlaceByPoint(true)
}

/** 手选一级地点：把坐标落到该地区内部，保证「地点名 = 坐标所在区域」 */
function onLocationPicked(name) {
  if (!name) {
    return
  }
  const loc = locations.value.find((item) => item.name === name)
  if (!loc) {
    return
  }
  const polygon = polygonOf(loc)
  if (polygon) {
    const point = interiorPoint(polygon)
    form.x = Math.round(point[0])
    form.y = Math.round(point[1])
  } else {
    // 还没画区域的单点地点：直接用它的标注点
    form.x = Number(loc.x == null ? form.x : loc.x)
    form.y = Number(loc.y == null ? form.y : loc.y)
  }
}

async function loadAiModels() {
  if (!aiProviderId.value) {
    ElMessage.warning('请先选择 AI 服务商')
    return
  }
  aiModelLoading.value = true
  try {
    aiModels.value = await aiProviderModels(aiProviderId.value)
    if (!aiModels.value.length) {
      ElMessage.warning('没有获取到模型，可手动输入模型名')
    }
  } finally {
    aiModelLoading.value = false
  }
}

async function onAiGenerate() {
  if (!aiRequirement.value.trim()) {
    ElMessage.warning('请先描述你想要的角色')
    return
  }
  if (!aiProviderId.value || !aiModel.value) {
    ElMessage.warning('请先选择 AI 服务商和模型')
    return
  }
  aiGenerating.value = true
  try {
    const draft = await generateSandboxCharacter({
      providerId: aiProviderId.value,
      model: aiModel.value,
      requirement: aiRequirement.value.trim()
    }, selectedWorldId.value)
    applyDraft(draft)
    ElMessage.success(
      draft && draft.combatPower != null
        ? `已生成（AI 评估战斗力 ${draft.combatPower}），请检查后保存`
        : '已生成，请检查后保存'
    )
  } finally {
    aiGenerating.value = false
  }
}

/** 把 AI 草稿填进新增角色表单 */
function applyDraft(draft) {
  if (!draft) return
  if (draft.name) {
    form.name = draft.name
  }
  form.title = draft.title || form.title
  form.appearance = draft.appearance || form.appearance
  form.persona = draft.persona || form.persona
  form.providerId = aiProviderId.value
  form.model = aiModel.value
  if (draft.x != null) {
    form.x = draft.x
  }
  if (draft.y != null) {
    form.y = draft.y
  }
  if (draft.coins != null) {
    form.coins = draft.coins
  }
  // AI 按角色描述与世界观评估出来的战斗力，直接填进表单（不再一律用默认 10）
  if (draft.combatPower != null) {
    form.combatPower = draft.combatPower
  }
  // 对实力/财富的态度：AI 生成时一并填充，管理员可以改
  form.powerView = draft.powerView || form.powerView
  form.wealthView = draft.wealthView || form.wealthView
  form.locationName = draft.locationName || ''
  form.subLocation = draft.subLocation || ''
  draftPlace.value = draft.locationName
    ? draft.subLocation
      ? `${draft.locationName} · ${draft.subLocation}`
      : draft.locationName
    : ''
  const status = draft.status || {}
  statusForm.体力 = numOr(status['体力'], 100)
  if (manaLabel.value) {
    statusForm[manaLabel.value] = numOr(status[manaLabel.value], 100)
  }
  statusForm.饥饿度 = numOr(status['饥饿度'], 20)
  statusForm.心情 = status['心情'] == null ? '平静' : String(status['心情'])
  const draftInjury = status['伤势'] == null ? '无恙' : String(status['伤势'])
  statusForm.伤势 = INJURY_LEVELS.includes(draftInjury) ? draftInjury : '无恙'
  extraStatus.value = Object.keys(status)
    .filter((key) => !standardStatusKeys().includes(key))
    .filter((key) => !(key === '魔力' && !manaLabel.value))
    .map((key) => ({ key, value: String(status[key]) }))
  draftItems.value = draft.items || []
}

function fillSample() {
  form.name = form.name || '魔女零'
  form.title = form.title || '流浪的星辉魔女'
  form.appearance =
    form.appearance ||
    '银白色长发，戴着一顶过大的黑色尖帽，旅行斗篷的下摆缀着细碎的星辉，手里常拿着一本被翻旧的魔法笔记。'
  form.persona =
    form.persona ||
    [
      '身份：已经活了三百年的魔女，外表却停留在少女模样，正在这个王国里漫无目的地旅行。',
      '性格：说话懒散、爱吐槽，但对弱小的事物格外温柔；好奇心极强，看到没见过的魔法就会走不动路。',
      '说话方式：喜欢用「唔」「嘛」开头，偶尔自称「本魔女」，很少说长篇大论。',
      '目标：收集散落在各地的星辉碎片，弄清自己记忆里缺失的那一晚发生了什么。',
      '能力：擅长星光与占星魔法，也会一些治愈术；不擅长做菜。',
      '禁忌：不会伤害无辜的人；被人叫她「大人」会不高兴；讨厌下雨天赶路。'
    ].join('\n')
}

function onProviderChange() {
  models.value = []
  form.model = ''
}

async function loadModels() {
  if (!form.providerId) {
    ElMessage.warning('请先选择 AI 服务商')
    return
  }
  modelLoading.value = true
  try {
    models.value = await aiProviderModels(form.providerId)
    if (!models.value.length) {
      ElMessage.warning('没有获取到模型，可手动输入模型名')
    }
  } finally {
    modelLoading.value = false
  }
}

async function onSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入角色名')
    return
  }
  const isNew = !form.id
  saving.value = true
  try {
    const saved = await saveSandboxCharacter({
      ...form,
      // 新建角色要指明属于哪个世界；编辑时后端已有记录
      worldId: form.id ? undefined : selectedWorldId.value,
      // 免遭遇地点：多选数组 → 逗号分隔字符串（服务端会再过滤成这个世界真实存在的地点）
      encounterExemptLocations: exemptLocations.value.length ? exemptLocations.value.join(',') : '',
      temperature: temperature.value,
      statusJson: buildStatusJson()
    })
    let firstActSummary = ''
    if (isNew && saved && saved.id) {
      // 1) AI 生成的初始物品一起放进背包
      for (const item of draftItems.value) {
        try {
          await saveSandboxItem({
            characterId: saved.id,
            name: item.name,
            quantity: item.quantity,
            rarity: item.rarity,
            description: item.description,
            // 随身装备：AI 给了槽位与加成就带着，服务端按品质区间夹取
            slot: item.slot || 'none',
            powerBonus: item.powerBonus == null ? 0 : item.powerBonus
          })
        } catch (e) {
          ElMessage.warning(`初始物品「${item.name}」添加失败，可在背包里手动补上`)
        }
      }
      // 2) 按需生成第一条行动（开局剧情）
      if (generateFirstAct.value) {
        try {
          const act = await runSandboxCharacter(saved.id)
          firstActSummary = act.summary || act.actions || ''
          // 第一条行动的结果也写进「最近执行结果」，避免只弹一个一闪而过的提示
          const fresh = await fetchNewestAct(saved.id)
          pushRunResult({
            characterId: saved.id,
            characterName: saved.name,
            ok: true,
            act: fresh || act
          })
          lastRunAt[saved.id] = timeText(fresh ? fresh.createTime : act.createTime)
        } catch (e) {
          ElMessage.warning('角色已创建，但第一条行动生成失败，可在列表里点「立即执行」重试')
          pushRunResult({
            characterId: saved.id,
            characterName: saved.name,
            ok: false,
            error: (e && e.message) || '第一条行动生成失败'
          })
        }
      }
    }
    ElMessage.success(firstActSummary ? `角色已创建，第一条行动：${firstActSummary}` : '角色已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onToggleEnabled(row, val) {
  await saveSandboxCharacter({ ...row, enabled: val ? 1 : 0 })
  row.enabled = val ? 1 : 0
  ElMessage.success(val ? '已开启该角色的自动行动' : '已停用该角色的自动行动')
  await load()
}

async function onRun(row) {
  // 同一个角色正在执行时忽略重复点击
  if (runningIds.value.includes(row.id)) {
    return
  }
  runningIds.value = [...runningIds.value, row.id]
  try {
    const act = await runSandboxCharacter(row.id)
    // 再取一次这条记录：列表接口会补上「移动 N km」这类服务端算好的字段，展示更完整
    const fresh = await fetchNewestAct(row.id)
    pushRunResult({
      characterId: row.id,
      characterName: row.name,
      ok: true,
      act: fresh || act
    })
    lastRunAt[row.id] = timeText(fresh ? fresh.createTime : act.createTime)
    ElMessage.success(`「${row.name}」执行完成，结果见下方「最近执行结果」`)
    await load()
  } catch (e) {
    // 失败也留一条记录，避免只看得到一闪而过的报错提示
    pushRunResult({
      characterId: row.id,
      characterName: row.name,
      ok: false,
      error: (e && e.message) || '执行失败'
    })
    lastRunAt[row.id] = nowTimeText()
  } finally {
    runningIds.value = runningIds.value.filter((id) => id !== row.id)
  }
}

/** 某个角色的「立即执行」是否正在运行 */
function isRunning(id) {
  return runningIds.value.includes(id)
}

// ============================== 最近执行结果 ==============================

/** 往结果面板顶部插入一条（超过上限就丢掉最旧的） */
function pushRunResult(entry) {
  runResults.value.unshift({
    id: Date.now() + Math.round(Math.random() * 1000),
    time: nowTimeText(),
    ...entry
  })
  if (runResults.value.length > RUN_RESULT_LIMIT) {
    runResults.value = runResults.value.slice(0, RUN_RESULT_LIMIT)
  }
}

/** 取某角色最新一条行动：列表接口会补上「移动 N km」，比直接用执行返回值更完整 */
async function fetchNewestAct(characterId) {
  try {
    const data = await sandboxActs({ characterId, page: 1, size: 1 })
    return (data.list || [])[0] || null
  } catch (e) {
    return null
  }
}

/** 「2026-09-17 12:34:56」→「12:34」；拿不到时间就退回当前时间 */
function timeText(value) {
  if (!value) {
    return nowTimeText()
  }
  const text = String(value)
  const index = text.indexOf(' ')
  return index > 0 ? text.slice(index + 1, index + 6) : text.slice(0, 5)
}

function nowTimeText() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

/** 移动距离文案（与前台一致：10 km 内保留一位小数） */
function formatKm(km) {
  const value = Number(km) || 0
  return value < 10 ? value.toFixed(1) + ' km' : Math.round(value) + ' km'
}

/** 结果里的地点文案：一级地点 + 二级地点 */
function placeText(act) {
  if (!act || !act.locationName) {
    return ''
  }
  return act.subLocation ? `${act.locationName} · ${act.subLocation}` : act.locationName
}

/** 跳到「行动日志」并按该角色筛选，方便看这条行动的完整上下文 */
function viewInActLog(item) {
  router.push({ path: '/admin/sandbox/acts', query: { characterId: item.characterId } })
}

/** 金币对账：按流水重算余额（历史上"整值回写"造成的丢失更新靠这个修复） */
async function onRepairCoins() {
  try {
    await ElMessageBox.confirm(
      '将按金币流水重算每个角色的余额，并把流水里的「当时余额」也重算一遍。只修数据、不删数据，确定继续吗？',
      '金币对账',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  repairing.value = true
  try {
    const report = await repairSandboxCoins(selectedWorldId.value)
    const details = report.details || []
    if (!details.length) {
      ElMessage.success(`检查了 ${report.checked || 0} 个角色，金币都没问题`)
      return
    }
    const lines = details.map((row) => `${row.characterName}：${row.before} → ${row.after}${row.logsFixed ? `（流水修正 ${row.logsFixed} 条）` : ''}`)
    await ElMessageBox.alert(
      `修正了 ${report.fixed || 0} 个角色的余额：<br/><br/>${lines.map((line) => `· ${line}`).join('<br/>')}`,
      '对账结果',
      { dangerouslyUseHTMLString: true }
    ).catch(() => {})
    await load()
  } finally {
    repairing.value = false
  }
}

/** 解除某个角色的执行锁（行动卡住时的应急出口） */
async function onUnlock(row) {
  try {
    await ElMessageBox.confirm(
      '解除执行锁只会清掉"正在执行"这个标记，不会中断服务器上可能还在跑的那次调用。'
        + '仅在角色一直显示正在执行、且你已经确认没有请求在跑时使用。确定吗？',
      '解除执行锁',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  await unlockSandboxCharacter(row.id)
  ElMessage.success(`已解除「${row.name}」的执行锁`)
  await load()
}

/**
 * 一键让所有启用角色各行动一次：多角色可以互相遇见、互动。
 *
 * 后端已经改成**异步执行**（角色多、单次调用慢的时候，同步请求会让浏览器先超时），
 * 所以这里点一下只是"启动"，然后每 5 秒轮询一次进度，直到跑完。
 */
async function onRunAll() {
  try {
    await ElMessageBox.confirm(
      '将让所有「启用」的角色各进行一次 AI 行动。同一地区的角色会依次行动（不同地区可以同时跑），' +
        '角色之间会互相遇见、互动。开始后可以继续做别的事，按钮上会显示进度。',
      '全员行动一轮',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  runningAll.value = true
  runAllProgressText.value = ''
  try {
    const first = await runAllSandboxCharacters(selectedWorldId.value)
    await applyRunAllItems(first)
    if (first.running === 1) {
      ElMessage.info(`已开始：共 ${first.total} 个角色，后台正在执行`)
      const final = await pollRunAllProgress()
      if (final) {
        await applyRunAllItems(final)
        ElMessage.success(
          `本轮完成：成功 ${final.success} 个，失败 ${final.failed} 个，详见下方「最近执行结果」`
        )
      }
    } else {
      ElMessage.success(`本轮完成：成功 ${first.success} 个，失败 ${first.failed} 个`)
    }
    await load()
  } finally {
    runningAll.value = false
    runAllProgressText.value = ''
  }
}

/** 每 5 秒查一次「全员行动」的进度，直到后台跑完 */
function pollRunAllProgress() {
  return new Promise((resolve) => {
    const timer = setInterval(async () => {
      try {
        const res = await sandboxRunAllProgress()
        runAllProgressText.value = `${res.done || 0}/${res.total || 0}`
        await applyRunAllItems(res)
        if (!res.running) {
          clearInterval(timer)
          resolve(res)
        }
      } catch (e) {
        clearInterval(timer)
        resolve(null)
      }
    }, 5000)
  })
}

/**
 * 把后台返回的每行结果写进「最近执行结果」面板。
 * 后台返回的是**累计**结果，所以每次整块重建，避免轮询时重复追加。
 */
async function applyRunAllItems(res) {
  if (!res || !res.items || !res.items.length) {
    return
  }
  runResults.value = []
  for (const line of res.items) {
    const idx = line.indexOf('：')
    if (idx <= 0) {
      continue
    }
    const name = line.slice(0, idx)
    const text = line.slice(idx + 1)
    const character = list.value.find((c) => c.name === name)
    const characterId = character ? character.id : null
    if (text.startsWith('失败')) {
      pushRunResult({
        characterId,
        characterName: name,
        ok: false,
        error: text.replace(/^失败（/, '').replace(/）$/, '')
      })
      if (character) {
        lastRunAt[character.id] = nowTimeText()
      }
    } else if (text.includes('跳过')) {
      // 「正在执行中」或「所在地区正有别人行动」都算跳过，不是失败
      pushRunResult({ characterId, characterName: name, ok: false, error: text })
    } else {
      const act = character ? await fetchNewestAct(character.id) : null
      pushRunResult({ characterId, characterName: name, ok: true, act })
      if (character && act) {
        lastRunAt[character.id] = timeText(act.createTime)
      }
    }
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(
    `确定删除角色「${row.name}」吗？它的行动记录与旅人低语也会一并删除。`,
    '提示',
    { type: 'warning' }
  )
  await deleteSandboxCharacter(row.id)
  ElMessage.success('已删除')
  await load()
}

async function openBackpack(row) {
  backpackCharacter.value = row
  backpackVisible.value = true
  newItem.name = ''
  newItem.quantity = 1
  newItem.description = ''
  await loadItems()
}

async function loadItems() {
  if (!backpackCharacter.value) return
  loadingItems.value = true
  try {
    backpackItems.value = await sandboxItems(backpackCharacter.value.id)
  } finally {
    loadingItems.value = false
  }
}

async function onAddItem() {
  if (!newItem.name.trim()) {
    ElMessage.warning('请输入物品名称')
    return
  }
  savingItem.value = true
  try {
    await saveSandboxItem({
      characterId: backpackCharacter.value.id,
      name: newItem.name.trim(),
      quantity: newItem.quantity,
      description: newItem.description
    })
    ElMessage.success('已添加')
    newItem.name = ''
    newItem.quantity = 1
    newItem.description = ''
    await loadItems()
  } finally {
    savingItem.value = false
  }
}

async function onUpdateItem(row) {
  await saveSandboxItem({
    characterId: row.characterId,
    name: row.name,
    quantity: row.quantity,
    rarity: row.rarity,
    icon: row.icon,
    description: row.description,
    // 装备字段：槽位与加成，服务端会按品质区间夹取；改成「非装备」会自动从装备栏取下
    slot: row.slot,
    powerBonus: row.powerBonus
  })
  ElMessage.success('已更新')
  await loadItems()
  await load()
}

async function onItemIconSuccess(row, res) {
  if (res && res.code === 200) {
    await saveSandboxItem({
      characterId: row.characterId,
      name: row.name,
      quantity: row.quantity,
      rarity: row.rarity,
      icon: res.data,
      description: row.description
    })
    ElMessage.success('图标已更新')
    await loadItems()
  } else {
    ElMessage.error((res && res.msg) || '上传失败')
  }
}

async function onDeleteItem(row) {
  await ElMessageBox.confirm(`确定把「${row.name}」从背包里删掉吗？`, '提示', { type: 'warning' })
  await deleteSandboxItem(row.id)
  ElMessage.success('已删除')
  await loadItems()
}

// ---------------- 装备栏 ----------------

/** 装备栏 4 格：把背包里 equipped=1 的物品按槽位对上 */
const equipmentRows = computed(() =>
  EQUIP_SLOTS.map((slot) => ({
    ...slot,
    item: backpackItems.value.find((item) => item.equipped === 1 && (item.slot || 'none') === slot.key) || null
  }))
)

/** 当前装备加成合计（破损的不算） */
const backpackEquipPower = computed(() =>
  equipmentRows.value.reduce((sum, row) => {
    if (!row.item || row.item.broken === 1) return sum
    return sum + (Number(row.item.powerBonus) || 0)
  }, 0)
)

const backpackTotalPower = computed(
  () => (Number(backpackCharacter.value && backpackCharacter.value.combatPower) || 10) + backpackEquipPower.value
)

async function onEquip(row) {
  try {
    await setSandboxItemEquip(row.id, 1)
    ElMessage.success(`已装备「${row.name}」`)
  } catch (e) {
    // 「拿不动」等规则由服务端拦下，错误信息直接来自后端
  }
  await loadItems()
  await load()
}

async function onUnequip(row) {
  await setSandboxItemEquip(row.id, 0)
  ElMessage.success(`已卸下「${row.name}」`)
  await loadItems()
  await load()
}

async function onRepairItem(row) {
  await repairSandboxItem(row.id)
  ElMessage.success(`已修复「${row.name}」`)
  await loadItems()
  await load()
}

async function onRefreshEquipPower() {
  if (!backpackCharacter.value) return
  const total = await refreshSandboxEquipPower(backpackCharacter.value.id)
  ElMessage.success(`已按装备栏重算：装备加成 +${total}`)
  await load()
}

onMounted(async () => {
  await initWorld()
  await loadProviders()
  await load()
})
</script>

<style scoped>
.tips { margin-bottom: 16px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.tip { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.range-sep { margin: 0 6px; color: var(--el-text-color-secondary); }
/* 最近执行结果面板 */
.run-results { margin-top: 16px; }
.run-stamp { margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; }
.run-item {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 10px;
  background: var(--el-fill-color-blank);
}
.run-item.failed { border-color: var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); }
.run-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.run-head .tip { margin-left: 0; }
.run-text { margin-top: 6px; white-space: pre-line; line-height: 1.7; font-size: 13px; }
.run-voice { margin-top: 4px; color: #a4638a; font-size: 13px; font-style: italic; }
.run-tags { margin-top: 6px; display: flex; gap: 8px; flex-wrap: wrap; }
.run-tag {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}
.run-error { margin-top: 6px; color: var(--el-color-danger); font-size: 13px; }
.avatar-thumb { width: 44px; height: 44px; border-radius: 10px; object-fit: cover; }
.avatar-empty {
  width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center;
  background: var(--el-fill-color-light); color: var(--el-text-color-secondary);
}
.avatar-preview { width: 90px; height: 90px; border-radius: 12px; object-fit: cover; display: block; }
.char-name { font-weight: 600; }
.char-title { color: var(--el-text-color-secondary); font-size: 12px; }
.coin-cell { color: #c9932a; font-weight: 600; }
.status-editor { width: 100%; }
.status-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.status-label { width: 48px; font-size: 13px; color: var(--el-text-color-regular); }
.item-add { display: flex; align-items: center; gap: 8px; margin-top: 14px; flex-wrap: wrap; }
.item-icon-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px dashed var(--el-border-color);
  font-size: 18px;
  cursor: pointer;
}
.item-icon-cell img { width: 28px; height: 28px; object-fit: contain; }
/* 装备栏：4 格表 + 加成小胶囊 */
.equip-table { margin-bottom: 10px; }
.item-name-cell { display: flex; align-items: center; gap: 6px; }
.item-equip-tag {
  padding: 0 7px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 17px;
  color: #2f5d8a;
  background: rgba(111, 168, 220, 0.16);
  border: 1px solid rgba(111, 168, 220, 0.45);
}
.item-equip-tag.broken {
  color: #a4443c;
  background: rgba(200, 90, 80, 0.14);
  border-color: rgba(200, 90, 80, 0.45);
}
.equip-cell { display: flex; align-items: center; gap: 8px; }
.item-icon-cell.small { width: 26px; height: 26px; font-size: 15px; border-style: solid; cursor: default; }
.item-icon-cell.small img { width: 22px; height: 22px; }
.equip-slot-name { font-size: 13px; color: var(--el-text-color-regular); }
.bonus-chip {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #8a5a2a;
  background: rgba(255, 176, 120, 0.2);
  border: 1px solid rgba(255, 176, 120, 0.5);
}
.equip-summary {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.equip-summary .sep { margin: 0 6px; color: var(--el-text-color-secondary); }
.muted-text { color: var(--el-text-color-secondary); font-size: 12px; }
.ai-box {
  width: 100%;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px dashed var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.ai-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.ai-row:last-child { margin-bottom: 0; }
.draft-place { margin-top: 6px; font-size: 13px; color: var(--el-color-primary); }
.draft-items { margin-top: 6px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 13px; }
.draft-title { color: var(--el-text-color-regular); }
.draft-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--el-border-color);
}
.draft-item em { font-style: normal; font-size: 12px; color: var(--el-text-color-secondary); }
.draft-item em.draft-equip { color: #2f5d8a; }
.draft-item button {
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 2px;
}
.draft-item button:hover { color: var(--el-color-danger); }
.world-option { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.world-option-name { overflow: hidden; text-overflow: ellipsis; }
.world-option-tag {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--el-color-warning);
  background: rgba(230, 162, 60, 0.14);
}
.combat-cell { color: #b0416b; font-weight: 600; }
.goal-cell { color: #4f9d8f; }
</style>
