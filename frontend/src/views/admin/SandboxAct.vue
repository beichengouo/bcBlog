<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>沙盒日志</span>
        <div class="toolbar-right">
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
          <el-select v-model="characterId" clearable placeholder="全部角色" style="width: 180px" @change="reload">
            <el-option v-for="c in characters" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select
            v-model="locationFilter"
            clearable
            placeholder="全部地点"
            style="width: 170px"
            @change="reload"
          >
            <el-option v-for="loc in locations" :key="'loc-' + loc.id" :label="loc.name" :value="loc.name" />
          </el-select>
          <el-button @click="reload">刷新</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="reload">
      <el-tab-pane label="行动日志" name="acts">
        <el-table :data="acts" v-loading="loadingActs">
          <el-table-column prop="createTime" label="时间" width="165" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">{{ characterName(row.characterId) }}</template>
          </el-table-column>
          <el-table-column label="地点" width="190">
            <template #default="{ row }">
              <div>{{ row.locationName || '—' }}<template v-if="row.subLocation"> · {{ row.subLocation }}</template></div>
              <div class="muted">x={{ row.x }} , y={{ row.y }}</div>
            </template>
          </el-table-column>
          <el-table-column label="做了什么" min-width="240">
            <template #default="{ row }">
              <div class="multiline">{{ row.actions || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="心声" min-width="200">
            <template #default="{ row }">
              <div class="multiline">{{ row.innerVoice || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="概括" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.summary || '—' }}</template>
          </el-table-column>
          <el-table-column label="运气" width="96">
            <template #default="{ row }">
              <span v-if="row.luck != null">{{ luckText(row.luck) }}（{{ row.luck > 0 ? '+' : '' }}{{ row.luck }}）</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="本步遭遇" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.encounter || '—' }}</template>
          </el-table-column>
          <el-table-column label="此刻的模样" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.look || '—' }}</template>
          </el-table-column>
          <el-table-column label="下次间隔" width="150">
            <template #default="{ row }">
              <span v-if="row.nextAfterMinutes > 0">
                {{ formatInterval(row.nextAfterMinutes) }}<em v-if="row.nextAfterReason">（{{ row.nextAfterReason }}）</em>
              </span>
              <span v-else class="muted">默认间隔</span>
            </template>
          </el-table-column>
          <el-table-column label="纪闻" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.newsRef">{{ row.newsRef }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="互动" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.companions" size="small" type="warning">与 {{ row.companions }} 互动</el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="委托" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row.questEvent" size="small" :type="questTagType(row.questEvent)">
                {{ row.questEvent }}
              </el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.fromAi === 1 ? 'success' : 'warning'">
                {{ row.fromAi === 1 ? (row.manual === 1 ? '手动执行' : 'AI 自动') : '解析失败' }}
              </el-tag>
              <el-tag v-if="row.reaction === 1" size="small" type="warning" class="react-tag">回应</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="onDeleteAct(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="actTotal"
          :page-size="pageSize"
          :current-page="actPage"
          @current-change="onActPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="旅人低语" name="whispers">
        <el-table :data="whispers" v-loading="loadingWhispers">
          <el-table-column prop="createTime" label="时间" width="165" />
          <el-table-column label="用户" width="160">
            <template #default="{ row }">
              <div class="user-cell">
                <img v-if="row.userAvatar" class="user-avatar" :src="row.userAvatar" alt="" />
                <span>{{ row.userName || ('用户#' + row.userId) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="120">
            <template #default="{ row }">{{ characterName(row.characterId) }}</template>
          </el-table-column>
          <el-table-column prop="content" label="低语内容" min-width="300" show-overflow-tooltip />
          <el-table-column label="消耗积分" width="100">
            <template #default="{ row }">{{ row.pointsCost }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="onDeleteWhisper(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="whisperTotal"
          :page-size="pageSize"
          :current-page="whisperPage"
          @current-change="onWhisperPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="金币流水" name="coins">
        <el-table :data="coinLogs" v-loading="loadingCoins">
          <el-table-column prop="createTime" label="时间" width="165" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">{{ characterName(row.characterId) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="coinTagType(row.type)">{{ coinTypeText(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金币变化" width="100">
            <template #default="{ row }">
              <span :class="row.coins > 0 ? 'plus' : 'minus'">{{ row.coins > 0 ? '+' : '' }}{{ row.coins }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="balance" label="变化后余额" width="110" />
          <el-table-column label="来源" width="150">
            <template #default="{ row }">{{ row.userName || '系统' }}</template>
          </el-table-column>
          <el-table-column label="消耗积分" width="100">
            <template #default="{ row }">{{ row.pointsCost || 0 }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="onDeleteCoinLog(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="coinTotal"
          :page-size="pageSize"
          :current-page="coinPage"
          @current-change="onCoinPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="角色关系" name="relations">
        <div class="tab-tools">
          <el-button type="primary" size="small" @click="openRelationEdit(null)">新增关系</el-button>
          <span class="muted">
            好感度 -100 ~ 100：AI 会根据角色互动自动加减，这里也可以手动修正；前台角色档案会展示双方对彼此的好感度
          </span>
        </div>
        <el-table :data="relations" v-loading="loadingRelations">
          <el-table-column label="角色" width="140">
            <template #default="{ row }">{{ row.characterName || ('角色#' + row.characterId) }}</template>
          </el-table-column>
          <el-table-column label="对谁" width="140">
            <template #default="{ row }">{{ row.targetName || ('角色#' + row.targetId) }}</template>
          </el-table-column>
          <el-table-column label="好感度" min-width="220">
            <template #default="{ row }">
              <div class="favor-cell">
                <el-progress
                  :percentage="favorPercent(row.favor)"
                  :stroke-width="8"
                  :show-text="false"
                  :color="favorColor(row.favor)"
                />
                <span class="favor-value">{{ row.favor }} · {{ row.favorLevel }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="对方对我" width="140">
            <template #default="{ row }">{{ row.reverseFavor }} · {{ row.reverseFavorLevel }}</template>
          </el-table-column>
          <el-table-column label="最近变化" width="110">
            <template #default="{ row }">
              <span v-if="row.lastChange" :class="row.lastChange > 0 ? 'plus' : 'minus'">
                {{ row.lastChange > 0 ? '+' : '' }}{{ row.lastChange }}
              </span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="updateTime" label="更新时间" width="165" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="openRelationEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="onDeleteRelation(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="每日记忆" name="memories">
        <div class="tab-tools">
          <span class="muted">日期</span>
          <el-date-picker
            v-model="memoryDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            placeholder="选择要生成哪一天的记忆"
            style="width: 170px"
            :disabled-date="(d) => d.getTime() > Date.now()"
          />
          <el-button size="small" @click="pickYesterday">昨天</el-button>
          <el-button size="small" :type="memoryFilterDate ? 'warning' : ''" @click="toggleMemoryFilter">
            {{ memoryFilterDate ? '只看 ' + memoryFilterDate : '只看这一天' }}
          </el-button>
          <el-button type="primary" size="small" :loading="summarizing" @click="onSummarize">
            生成这一天的记忆
          </el-button>
          <span class="muted">
            每天到点后会自动为当天有行动的角色生成记忆（默认 23:50），用于后续几天的活动；
            这里可以选任意日期补生成（例如昨天漏了）——<b>同一天已有记忆会被覆盖</b>，
            那天没有任何行动的角色会跳过。保留天数在「系统设置 → 数据清理」里配置
          </span>
        </div>
        <el-table :data="memories" v-loading="loadingMemories">
          <el-table-column prop="memoryDate" label="日期" width="120" />
          <el-table-column label="角色" width="130">
            <template #default="{ row }">{{ characterName(row.characterId) }}</template>
          </el-table-column>
          <el-table-column label="记忆内容" min-width="360">
            <template #default="{ row }">
              <div class="multiline">{{ row.summary || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="当天行动" width="100">
            <template #default="{ row }">{{ row.actCount || 0 }} 条</template>
          </el-table-column>
          <el-table-column label="来源" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.fromAi === 1 ? 'success' : 'warning'">
                {{ row.fromAi === 1 ? 'AI 总结' : '兜底拼接' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="165" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="openMemoryEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="onDeleteMemory(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="memoryTotal"
          :page-size="pageSize"
          :current-page="memoryPage"
          @current-change="onMemoryPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="旅人纪闻" name="news">
        <div class="tab-tools">
          <el-select v-model="newsDate" style="width: 150px" @change="loadNews">
            <el-option label="今天" value="" />
            <el-option label="全部日期" value="all" />
          </el-select>
          <el-date-picker
            v-model="newsDatePick"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择某天"
            style="width: 150px"
            @change="onNewsDatePick"
          />
          <el-button type="primary" :loading="generatingNews" @click="onGenerateNews">生成事件</el-button>
          <el-button @click="newsSettingVisible = true">栏目设置</el-button>
          <el-button @click="openNewsEdit(null)">手动新增</el-button>
          <span class="muted">事件由 AI 独立生成（参考世界观与最近动向），只展示当天，第二天自动清理</span>
        </div>
        <el-table :data="newsList" v-loading="loadingNews">
          <el-table-column prop="newsDate" label="日期" width="110" />
          <el-table-column label="重要度" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.level === 3 ? 'danger' : row.level === 2 ? 'warning' : 'info'">
                {{ row.level === 3 ? '重大' : row.level === 2 ? '重要' : '普通' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="事件" min-width="240" show-overflow-tooltip />
          <el-table-column prop="content" label="补充说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="locationName" label="发生地" width="130" />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.source === 'ai' ? 'success' : 'primary'">
                {{ row.source === 'ai' ? 'AI' : '手动' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="置顶" width="80">
            <template #default="{ row }">
              <el-switch
                size="small"
                :model-value="row.pinned === 1"
                @change="(val) => onToggleNews(row, 'pinned', val)"
              />
            </template>
          </el-table-column>
          <el-table-column label="前台显示" width="100">
            <template #default="{ row }">
              <el-switch
                size="small"
                :model-value="row.enabled === 1"
                @change="(val) => onToggleNews(row, 'enabled', val)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="openNewsEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="onDeleteNews(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="newsTotal"
          :page-size="pageSize"
          :current-page="newsPage"
          @current-change="onNewsPageChange"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 纪闻编辑 -->
    <el-dialog v-model="newsVisible" :title="newsForm.id ? '编辑事件' : '新增事件'" width="min(94vw, 620px)">
      <el-form :model="newsForm" label-width="90px">
        <el-form-item label="事件">
          <el-input v-model="newsForm.title" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="一句话事件，例如：白鸦村举行一年一度的丰收庆典" />
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="newsForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="发生地">
          <el-select v-model="newsForm.locationName" filterable allow-create clearable placeholder="选择或输入地点" style="width: 100%">
            <el-option v-for="loc in locations" :key="'nl-' + loc.id" :label="loc.name" :value="loc.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="重要度">
          <el-radio-group v-model="newsForm.level">
            <el-radio :value="1">普通</el-radio>
            <el-radio :value="2">重要</el-radio>
            <el-radio :value="3">重大</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="newsForm.newsDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="newsForm.pinned" :active-value="1" :inactive-value="0" />
          <span class="tip">置顶的事件排在最前面（当天内有效）</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="newsForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newsVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingNews" @click="onSaveNews">保存</el-button>
      </template>
    </el-dialog>

    <!-- 纪闻栏目设置 -->
    <el-dialog v-model="newsSettingVisible" title="旅人纪闻设置" width="min(94vw, 560px)">
      <el-form :model="newsSetting" label-width="110px">
        <el-form-item label="栏目名称">
          <el-input v-model="newsSetting.newsTitle" maxlength="20" placeholder="旅人纪闻" />
        </el-form-item>
        <el-form-item label="前台显示">
          <el-switch v-model="newsSetting.newsEnabled" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="每次生成条数">
          <el-input v-model="newsSetting.newsPerGenerate" style="width: 90px" />
          <span class="tip">生成事件时默认写几条（1~10）</span>
        </el-form-item>
        <el-form-item label="自动生成">
          <el-switch v-model="newsSetting.newsAutoEnabled" active-value="1" inactive-value="0" />
          <span class="tip">开启后按下面的间隔自动生成；一批不够时会把间隔调小，多出的批次叠加在当天的纪闻里</span>
        </el-form-item>
        <el-form-item label="刷新间隔 / 首次时间">
          <el-input v-model="newsSetting.newsIntervalHours" style="width: 90px" />
          <span class="range-sep">小时 · 当天首次</span>
          <el-input v-model="newsSetting.newsAutoTime" style="width: 90px" placeholder="07:00" />
          <span class="tip">
            间隔填 24 就是每天一次；填 6 就是 07:00 / 13:00 / 19:00 / 01:00 一天四次
          </span>
        </el-form-item>
        <el-form-item label="生成用服务商">
          <el-select v-model="newsSetting.newsProviderId" clearable placeholder="默认服务商" style="width: 200px" @change="onNewsProviderChange">
            <el-option v-for="p in providers" :key="'np-' + p.id" :label="p.name" :value="String(p.id)" />
          </el-select>
          <el-button :loading="newsModelLoading" @click="loadNewsModels">获取模型</el-button>
        </el-form-item>
        <el-form-item label="生成用模型">
          <el-select v-model="newsSetting.newsModel" filterable allow-create clearable placeholder="选择或输入模型" style="width: 260px">
            <el-option v-for="m in newsModels" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="附加要求">
          <el-input v-model="newsSetting.newsPromptExtra" type="textarea" :rows="3" maxlength="300" show-word-limit
                    placeholder="可选，例如：多写一些节庆与商队相关的事件" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newsSettingVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingNewsSetting" @click="onSaveNewsSetting">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memoryVisible" title="编辑记忆" width="min(94vw, 620px)">
      <el-form :model="memoryForm" label-width="80px">
        <el-form-item label="角色">
          <span>{{ characterName(memoryForm.characterId) }}</span>
          <span class="muted" style="margin-left: 10px">{{ memoryForm.memoryDate }}</span>
        </el-form-item>
        <el-form-item label="记忆">
          <el-input v-model="memoryForm.summary" type="textarea" :rows="10" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memoryVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingMemory" @click="onSaveMemory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationVisible" :title="relationForm.id ? '编辑关系' : '新增关系'" width="min(92vw, 460px)">
      <el-form :model="relationForm" label-width="90px">
        <el-form-item label="角色">
          <el-select v-model="relationForm.characterId" placeholder="选择角色" style="width: 100%">
            <el-option v-for="c in characters" :key="'rc-' + c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对谁">
          <el-select v-model="relationForm.targetId" placeholder="选择对象" style="width: 100%">
            <el-option
              v-for="c in characters"
              :key="'rt-' + c.id"
              :label="c.name"
              :value="c.id"
              :disabled="c.id === relationForm.characterId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="好感度">
          <el-slider v-model="relationForm.favor" :min="-100" :max="100" :step="1" show-input />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="relationForm.remark" maxlength="200" placeholder="例如：在集市认识、一起做过委托" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRelation" @click="onSaveRelation">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  sandboxCharacters,
  sandboxLocations,
  sandboxWorlds,
  sandboxActs,
  deleteSandboxAct,
  sandboxInteractions,
  deleteSandboxInteraction,
  sandboxCoinLogs,
  deleteSandboxCoinLog,
  sandboxRelations,
  saveSandboxRelation,
  deleteSandboxRelation,
  sandboxMemories,
  saveSandboxMemory,
  deleteSandboxMemory,
  summarizeSandboxMemories,
  sandboxNewsList,
  saveSandboxNews,
  deleteSandboxNews,
  generateSandboxNews,
  sandboxSettings,
  saveSandboxNewsSettings
} from '@/api/sandbox'
import { aiProviderList, aiProviderModels } from '@/api/ai'
import { useSandboxWorld } from '@/composables/useSandboxWorld'
const route = useRoute()

const tab = ref('acts')
/** 当前世界（三个沙盒页面共用一个选择） */
const worlds = ref([])
const { currentWorldId, setCurrentWorld } = useSandboxWorld()
const selectedWorldId = ref(null)
const characters = ref([])
const characterId = ref(null)
const locations = ref([])
const locationFilter = ref('')

const acts = ref([])
const actTotal = ref(0)
const actPage = ref(1)
const loadingActs = ref(false)

const whispers = ref([])
const whisperTotal = ref(0)
const whisperPage = ref(1)
const loadingWhispers = ref(false)

const coinLogs = ref([])
const coinTotal = ref(0)
const coinPage = ref(1)
const loadingCoins = ref(false)

const relations = ref([])
const loadingRelations = ref(false)
const relationVisible = ref(false)
const savingRelation = ref(false)
const relationForm = reactive({ id: null, characterId: null, targetId: null, favor: 0, remark: '' })

const memories = ref([])
const memoryTotal = ref(0)
const memoryPage = ref(1)
const loadingMemories = ref(false)
const memoryVisible = ref(false)
const savingMemory = ref(false)
const summarizing = ref(false)
/** 「每日记忆」要生成哪一天：默认今天，可改成昨天等任意过去日期补生成 */
const memoryDate = ref(todayStr())
/** 记忆列表的日期过滤（空 = 全部日期）；补生成后会自动只看那一天，方便立刻核对 */
const memoryFilterDate = ref('')
const memoryForm = reactive({ id: null, characterId: null, memoryDate: '', summary: '' })

const newsList = ref([])
const newsTotal = ref(0)
const newsPage = ref(1)
const loadingNews = ref(false)
const newsDate = ref('')
const newsDatePick = ref('')
const newsVisible = ref(false)
const savingNews = ref(false)
const generatingNews = ref(false)
const newsForm = reactive({
  id: null,
  title: '',
  content: '',
  locationName: '',
  level: 1,
  newsDate: '',
  pinned: 0,
  enabled: 1
})
const newsSettingVisible = ref(false)
const savingNewsSetting = ref(false)
const newsModelLoading = ref(false)
const providers = ref([])
const newsModels = ref([])
const newsSetting = reactive({
  newsTitle: '旅人纪闻',
  newsEnabled: '1',
  newsPerGenerate: '3',
  newsProviderId: '',
  newsModel: '',
  newsPromptExtra: '',
  newsAutoEnabled: '1',
  newsAutoTime: '07:00',
  newsIntervalHours: '24'
})

const pageSize = 10

async function loadCharacters() {
  try {
    characters.value = await sandboxCharacters(selectedWorldId.value)
  } catch (e) {
    characters.value = []
  }
  try {
    locations.value = await sandboxLocations(selectedWorldId.value)
  } catch (e) {
    locations.value = []
  }
}

/** 切换世界：清掉角色/地点筛选后重新加载（与其它两个沙盒页面共用选择） */
async function onSwitchWorld(id) {
  setCurrentWorld(id)
  characterId.value = null
  locationFilter.value = ''
  await loadCharacters()
  await loadActs()
}

/** 首次进入：定位到当前世界 */
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

function characterName(id) {
  const hit = characters.value.find((c) => c.id === id)
  return hit ? hit.name : `角色#${id}`
}

/** 委托徽章的颜色：完成绿色、接取蓝色、放弃灰色、其余（推进/自检）用默认 */
function questTagType(event) {
  if (!event) return 'info'
  if (event.indexOf('完成') >= 0) return 'success'
  if (event.indexOf('接取') >= 0) return 'primary'
  if (event.indexOf('放弃') >= 0) return 'info'
  return 'warning'
}

/** 本步运气的文字档位（-3 大凶 ~ +3 大吉），与后端 luckText 保持一致 */
function luckText(luck) {
  const names = { 3: '大吉', 2: '走运', 1: '小顺', 0: '平常', '-1': '小背', '-2': '倒霉', '-3': '大凶' }
  return names[String(luck)] || '平常'
}

async function loadActs() {
  loadingActs.value = true
  try {
    const data = await sandboxActs({
      characterId: characterId.value || undefined,
      locationName: locationFilter.value || undefined,
      worldId: selectedWorldId.value || undefined,
      page: actPage.value,
      size: pageSize
    })
    acts.value = data.list || []
    actTotal.value = data.total || 0
  } finally {
    loadingActs.value = false
  }
}

async function loadWhispers() {
  loadingWhispers.value = true
  try {
    const data = await sandboxInteractions({
      characterId: characterId.value || undefined,
      page: whisperPage.value,
      size: pageSize
    })
    whispers.value = data.list || []
    whisperTotal.value = data.total || 0
  } finally {
    loadingWhispers.value = false
  }
}

function reload() {
  if (tab.value === 'acts') {
    loadActs()
  } else if (tab.value === 'whispers') {
    loadWhispers()
  } else if (tab.value === 'coins') {
    loadCoinLogs()
  } else if (tab.value === 'memories') {
    loadMemories()
  } else if (tab.value === 'news') {
    loadNews()
  } else {
    loadRelations()
  }
}

async function loadNews() {
  loadingNews.value = true
  try {
    const data = await sandboxNewsList({
      date: newsDate.value || undefined,
      worldId: selectedWorldId.value || undefined,
      page: newsPage.value,
      size: pageSize
    })
    newsList.value = data.list || []
    newsTotal.value = data.total || 0
  } finally {
    loadingNews.value = false
  }
}

function onNewsPageChange(page) {
  newsPage.value = page
  loadNews()
}

function onNewsDatePick(value) {
  newsDate.value = value || ''
  newsPage.value = 1
  loadNews()
}

function openNewsEdit(row) {
  if (row) {
    Object.assign(newsForm, {
      id: row.id,
      title: row.title,
      content: row.content || '',
      locationName: row.locationName || '',
      level: row.level || 1,
      newsDate: row.newsDate,
      pinned: row.pinned || 0,
      enabled: row.enabled == null ? 1 : row.enabled
    })
  } else {
    Object.assign(newsForm, {
      id: null,
      title: '',
      content: '',
      locationName: '',
      level: 1,
      newsDate: new Date().toISOString().slice(0, 10),
      pinned: 0,
      enabled: 1
    })
  }
  newsVisible.value = true
}

async function onSaveNews() {
  if (!newsForm.title.trim()) {
    ElMessage.warning('请填写事件内容')
    return
  }
  savingNews.value = true
  try {
    await saveSandboxNews({ ...newsForm, source: 'admin' })
    ElMessage.success('已保存')
    newsVisible.value = false
    await loadNews()
  } finally {
    savingNews.value = false
  }
}

async function onDeleteNews(row) {
  await ElMessageBox.confirm('确定删除这条事件吗？', '提示', { type: 'warning' })
  await deleteSandboxNews(row.id)
  ElMessage.success('已删除')
  await loadNews()
}

/** 列表里一键切换置顶 / 前台显示 */
async function onToggleNews(row, field, value) {
  const flag = value ? 1 : 0
  await saveSandboxNews({
    id: row.id,
    title: row.title,
    content: row.content,
    locationName: row.locationName,
    level: row.level,
    newsDate: row.newsDate,
    source: row.source || 'admin',
    pinned: field === 'pinned' ? flag : row.pinned,
    enabled: field === 'enabled' ? flag : row.enabled
  })
  row[field] = flag
  ElMessage.success(field === 'pinned' ? (flag ? '已置顶' : '已取消置顶') : flag ? '已在前台显示' : '已停止显示')
}

/** 由 AI 生成当天事件 */
async function onGenerateNews() {
  try {
    await ElMessageBox.confirm('将按「栏目设置」里的服务商与模型生成若干条当天事件，确定继续吗？', '生成旅人纪闻', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  generatingNews.value = true
  try {
    const count = await generateSandboxNews({ worldId: selectedWorldId.value || undefined })
    ElMessage.success(`已生成 ${count} 条事件`)
    await loadNews()
  } finally {
    generatingNews.value = false
  }
}

async function loadNewsSetting() {
  try {
    const data = await sandboxSettings()
    newsSetting.newsTitle = data.newsTitle || '旅人纪闻'
    newsSetting.newsEnabled = data.newsEnabled === undefined ? '1' : data.newsEnabled
    newsSetting.newsPerGenerate = data.newsPerGenerate || '3'
    newsSetting.newsProviderId = data.newsProviderId || ''
    newsSetting.newsModel = data.newsModel || ''
    newsSetting.newsPromptExtra = data.newsPromptExtra || ''
    newsSetting.newsAutoEnabled = data.newsAutoEnabled === undefined ? '1' : data.newsAutoEnabled
    newsSetting.newsAutoTime = data.newsAutoTime || '07:00'
    newsSetting.newsIntervalHours = data.newsIntervalHours || '24'
  } catch (e) {
    // 读取失败时保留默认值
  }
  try {
    providers.value = await aiProviderList()
  } catch (e) {
    providers.value = []
  }
}

function onNewsProviderChange() {
  newsModels.value = []
  newsSetting.newsModel = ''
}

async function loadNewsModels() {
  if (!newsSetting.newsProviderId) {
    ElMessage.warning('请先选择服务商')
    return
  }
  newsModelLoading.value = true
  try {
    newsModels.value = await aiProviderModels(Number(newsSetting.newsProviderId))
    if (!newsModels.value.length) {
      ElMessage.warning('没有获取到模型，可手动输入模型名')
    }
  } finally {
    newsModelLoading.value = false
  }
}

async function onSaveNewsSetting() {
  savingNewsSetting.value = true
  try {
    // 用纪闻专用接口：只写纪闻相关配置，改不到世界运行参数
    await saveSandboxNewsSettings({ ...newsSetting })
    ElMessage.success('设置已保存')
    newsSettingVisible.value = false
  } finally {
    savingNewsSetting.value = false
  }
}

async function loadMemories() {
  loadingMemories.value = true
  try {
    const data = await sandboxMemories({
      characterId: characterId.value || undefined,
      date: memoryFilterDate.value || undefined,
      page: memoryPage.value,
      size: pageSize
    })
    memories.value = data.list || []
    memoryTotal.value = data.total || 0
  } finally {
    loadingMemories.value = false
  }
}

function onMemoryPageChange(page) {
  memoryPage.value = page
  loadMemories()
}

function openMemoryEdit(row) {
  memoryForm.id = row.id
  memoryForm.characterId = row.characterId
  memoryForm.memoryDate = row.memoryDate
  memoryForm.summary = row.summary || ''
  memoryVisible.value = true
}

async function onSaveMemory() {
  if (!memoryForm.summary.trim()) {
    ElMessage.warning('记忆内容不能为空')
    return
  }
  savingMemory.value = true
  try {
    await saveSandboxMemory({
      characterId: memoryForm.characterId,
      memoryDate: memoryForm.memoryDate,
      summary: memoryForm.summary
    })
    ElMessage.success('记忆已保存')
    memoryVisible.value = false
    await loadMemories()
  } finally {
    savingMemory.value = false
  }
}

async function onDeleteMemory(row) {
  await ElMessageBox.confirm('确定删除这条记忆吗？', '提示', { type: 'warning' })
  await deleteSandboxMemory(row.id)
  ElMessage.success('已删除')
  await loadMemories()
}

/** 今天（yyyy-MM-dd，按本地时区） */
function todayStr() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 一键切到昨天：最常用来补"昨天忘了生成" */
function pickYesterday() {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  const pad = (n) => String(n).padStart(2, '0')
  memoryDate.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 列表只看"当前选中的这一天" / 切回全部 */
async function toggleMemoryFilter() {
  memoryFilterDate.value = memoryFilterDate.value ? '' : (memoryDate.value || todayStr())
  memoryPage.value = 1
  await loadMemories()
}

async function onSummarize() {
  const date = memoryDate.value || todayStr()
  const isToday = date === todayStr()
  try {
    await ElMessageBox.confirm(
      `将为「${date} 有行动」的所有角色各调用一次 AI 生成记忆`
        + `（${isToday ? '今天' : date}已有记忆会<b>覆盖</b>；那天没行动的角色会跳过）。确定继续吗？`,
      '生成每日记忆',
      { type: 'warning', dangerouslyUseHTMLString: true }
    )
  } catch (e) {
    return
  }
  summarizing.value = true
  try {
    // 接口返回"实际写入/覆盖了几条"（0 表示这一天没有任何角色行动，不用慌）
    const written = await summarizeSandboxMemories(date)
    if (written > 0) {
      ElMessage.success(`已为 ${date} 生成/覆盖 ${written} 条记忆`)
    } else {
      ElMessage.warning(`${date} 没有任何角色行动，没有生成记忆（可以先确认那天的行动日志还在）`)
    }
    // 生成完自动切到"只看这一天"，方便立刻核对结果
    memoryFilterDate.value = date
    memoryPage.value = 1
    await loadMemories()
  } finally {
    summarizing.value = false
  }
}

async function loadRelations() {
  loadingRelations.value = true
  try {
    relations.value = await sandboxRelations({ characterId: characterId.value || undefined })
  } finally {
    loadingRelations.value = false
  }
}

/** 好感度 -100~100 映射到 0~100 的进度条 */
function favorPercent(favor) {
  const value = Number(favor || 0)
  return Math.max(0, Math.min(100, Math.round((value + 100) / 2)))
}

function favorColor(favor) {
  const value = Number(favor || 0)
  if (value < 0) return '#e2664f'
  if (value < 40) return '#b9a5c9'
  if (value < 80) return '#7bc47f'
  return '#f2b23e'
}

function openRelationEdit(row) {
  if (row) {
    relationForm.id = row.id
    relationForm.characterId = row.characterId
    relationForm.targetId = row.targetId
    relationForm.favor = row.favor == null ? 0 : row.favor
    relationForm.remark = row.remark || ''
  } else {
    relationForm.id = null
    relationForm.characterId = characters.value.length ? characters.value[0].id : null
    relationForm.targetId = characters.value.length > 1 ? characters.value[1].id : null
    relationForm.favor = 0
    relationForm.remark = ''
  }
  relationVisible.value = true
}

async function onSaveRelation() {
  if (!relationForm.characterId || !relationForm.targetId) {
    ElMessage.warning('请选择角色与对象')
    return
  }
  if (relationForm.characterId === relationForm.targetId) {
    ElMessage.warning('角色不能和自己建立好感度')
    return
  }
  savingRelation.value = true
  try {
    await saveSandboxRelation({ ...relationForm })
    ElMessage.success('好感度已保存')
    relationVisible.value = false
    await loadRelations()
  } finally {
    savingRelation.value = false
  }
}

async function onDeleteRelation(row) {
  await ElMessageBox.confirm('确定删除这条好感度记录吗？', '提示', { type: 'warning' })
  await deleteSandboxRelation(row.id)
  ElMessage.success('已删除')
  await loadRelations()
}

async function loadCoinLogs() {
  loadingCoins.value = true
  try {
    const data = await sandboxCoinLogs({
      characterId: characterId.value || undefined,
      page: coinPage.value,
      size: pageSize
    })
    coinLogs.value = data.list || []
    coinTotal.value = data.total || 0
  } finally {
    loadingCoins.value = false
  }
}

function onCoinPageChange(page) {
  coinPage.value = page
  loadCoinLogs()
}

/** 把分钟格式化成「6 小时」「40 分钟」 */
function formatInterval(minutes) {
  const value = Number(minutes) || 0
  if (value <= 0) {
    return '—'
  }
  if (value < 60) {
    return `${value} 分钟`
  }
  const hours = Math.floor(value / 60)
  const mins = value % 60
  return mins ? `${hours} 小时 ${mins} 分钟` : `${hours} 小时`
}

function coinTypeText(type) {
  if (type === 'contribute') return '旅人贡献'
  // earn = AI 叙述里赚到的工钱（打工、卖东西…）；quest = 服务端结算的委托报酬，两者要分清
  if (type === 'earn') return '临时工钱'
  if (type === 'spend') return '日常消耗'
  if (type === 'shop_buy') return '集市购物'
  if (type === 'quest') return '委托报酬'
  if (type === 'init') return '初始金币'
  if (type === 'admin') return '管理员调整'
  return '其他变动'
}

function coinTagType(type) {
  if (type === 'contribute') return 'warning'
  if (type === 'earn') return 'success'
  if (type === 'spend') return 'info'
  if (type === 'shop_buy') return 'success'
  if (type === 'init') return 'info'
  return 'primary'
}

async function onDeleteCoinLog(row) {
  await ElMessageBox.confirm('确定删除这条金币流水吗？（只删记录，不改变角色金币余额）', '提示', { type: 'warning' })
  await deleteSandboxCoinLog(row.id)
  ElMessage.success('已删除')
  await loadCoinLogs()
}

function onActPageChange(page) {
  actPage.value = page
  loadActs()
}

function onWhisperPageChange(page) {
  whisperPage.value = page
  loadWhispers()
}

async function onDeleteAct(row) {
  await ElMessageBox.confirm('确定删除这条行动记录吗？', '提示', { type: 'warning' })
  await deleteSandboxAct(row.id)
  ElMessage.success('已删除')
  await loadActs()
}

async function onDeleteWhisper(row) {
  await ElMessageBox.confirm('确定删除这条旅人低语吗？', '提示', { type: 'warning' })
  await deleteSandboxInteraction(row.id)
  ElMessage.success('已删除')
  await loadWhispers()
}

onMounted(async () => {
  // 支持从其它页面带 ?characterId= 跳进来（例如「沙盒角色」的执行结果点"在行动日志里查看"）
  const queryCharacter = Number(route.query.characterId)
  if (queryCharacter) {
    characterId.value = queryCharacter
  }
  await initWorld()
  await loadCharacters()
  await loadActs()
  await loadNewsSetting()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.multiline { white-space: pre-line; line-height: 1.6; }
.tip { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.range-sep { margin: 0 8px; color: var(--el-text-color-secondary); }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.user-cell { display: flex; align-items: center; gap: 8px; }
.user-avatar { width: 26px; height: 26px; border-radius: 50%; object-fit: cover; }
.pager { margin-top: 14px; justify-content: flex-end; }
.plus { color: #3f9e4d; font-weight: 600; }
.minus { color: #d4553f; font-weight: 600; }
.tab-tools { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.react-tag { margin-left: 4px; }
.favor-cell :deep(.el-progress) { margin-bottom: 4px; }
.favor-value { font-size: 12px; color: var(--el-text-color-regular); }
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
</style>
