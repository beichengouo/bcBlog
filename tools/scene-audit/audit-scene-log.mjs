/**
 * 沙盒模拟报告的"故事逻辑体检"：直接读 AI 调用日志（target/probe/ai-log-scene-*.md），
 * 把每一次角色行动的主调用解析出来，然后跑和服务端同一套 L1 规则，数一数每种问题出现多少次。
 *
 * 用途：对比"命中才查 / 每步都查"两轮模拟的故事质量——同一套规则，数问题条数即可。
 *
 * 用法：node tools/scene-audit/audit-scene-log.mjs target/probe/ai-log-scene-real-suspicious.md [更多文件...]
 */
import { readFile } from 'node:fs/promises';

const MAIN_ACTIONS = /沙盒·(立即执行一次|自动行动|回应回合|自动回应)/;
const HANDLE_WORDS = ['战', '斗', '打', '迎', '逃', '躲', '藏', '退', '闪', '伤', '击', '挥', '射',
  '跑', '对峙', '僵持', '扔', '砍', '刺', '格挡', '摔', '翻'];
const MONEY_WORDS = ['金', '钱', '币', '买', '卖', '付', '赚', '收', '费', '账', '报酬', '赏金',
  '工钱', '打点', '押金', '赔偿', '罚', '租', '赊'];

function extractBlocks(text) {
  return text.split('<!-- =====').slice(1).map((chunk) => {
    // 注意：按 "<!-- =====" 切开后，头部前面会多一个空格，匹配时间前先 trim
    const header = chunk.split('\n')[0].trim();
    const action = (header.split('|')[0] || '').replace(/^\[[^\]]+\]\s*/, '').trim();
    const time = (/^\[([^\]]+)\]/.exec(header) || [])[1] || '';
    const respAt = chunk.indexOf('### 返回');
    const response = respAt < 0 ? '' : chunk.slice(respAt);
    return { action, time, chunk, response };
  });
}

/** 从响应里取出 <final> 段的 JSON */
function finalJson(response) {
  const marker = response.lastIndexOf('<final>');
  const tail = marker >= 0 ? response.slice(marker) : response;
  const start = tail.indexOf('{');
  if (start < 0) return null;
  let depth = 0;
  let inStr = false;
  let escape = false;
  for (let i = start; i < tail.length; i++) {
    const ch = tail[i];
    if (escape) { escape = false; continue; }
    if (ch === '\\') { escape = true; continue; }
    if (ch === '"') { inStr = !inStr; continue; }
    if (inStr) continue;
    if (ch === '{') depth++;
    else if (ch === '}') {
      depth--;
      if (depth === 0) {
        try { return JSON.parse(tail.slice(start, i + 1)); } catch { return null; }
      }
    }
  }
  return null;
}

function charName(chunk) {
  const m = /姓名：([^\n]+)/.exec(chunk);
  return m ? m[1].trim() : '(未知)';
}

/**
 * 这一次调用的用户提示词里「世界里的其他居民」列了谁。
 * 服务端写进 act.companions 之前会用"同区域 + 距离上限"过滤一遍，
 * 提示词里列出来的人才是它认可的"附近的人"，用它来避免误判（远处的人本来就会被丢掉）。
 */
function nearbyNames(chunk) {
  const at = chunk.indexOf('【世界里的其他居民】');
  if (at < 0) return [];
  const block = chunk.slice(at, at + 4000);
  const names = [];
  const lines = block.split('\n');
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    if (!line.startsWith('- ')) {
      if (names.length) break;
      continue;
    }
    const m = /^-\s*([^\n（(：:]+)/.exec(line);
    if (m) names.push(m[1].trim());
  }
  return names;
}

/** 兼容 actions 是数组或字符串两种写法 */
function actionsText(json) {
  const raw = json.actions;
  if (Array.isArray(raw)) return raw.join('\n');
  return raw == null ? '' : String(raw);
}

function textOf(json) {
  return [actionsText(json), json.summary || ''].join('');
}

function hasAny(text, words) {
  return words.some((word) => text.includes(word));
}

async function audit(file) {
  const text = await readFile(file, 'utf8');
  const blocks = extractBlocks(text);
  const acts = [];
  let selfChecks = 0;
  let selfCheckFixed = 0;
  const selfCheckIssues = [];

  for (const block of blocks) {
    if (block.action === '沙盒·每步自检') {
      selfChecks++;
      const json = finalJson(block.response);
      if (json && json.ok === false) {
        selfCheckFixed++;
        (json.issues || []).forEach((issue) => selfCheckIssues.push(String(issue).slice(0, 60)));
      }
      continue;
    }
    if (!MAIN_ACTIONS.test(block.action)) continue;
    const json = finalJson(block.response);
    if (!json) continue;
    acts.push({
      time: block.time,
      name: charName(block.chunk),
      location: json.location,
      sub: json.sub_location,
      companions: Array.isArray(json.companions) ? json.companions.filter(Boolean) : [],
      nearby: nearbyNames(block.chunk),
      coinChange: Number(json.coins_change || 0),
      actions: actionsText(json),
      summary: json.summary || '',
      encounter: /【本步遭遇·必须处理】/.test(block.chunk) ? block.chunk.match(/【本步遭遇·必须处理】\n([^\n]+)/)?.[1] || '' : ''
    });
  }

  const issues = { 同伴位置对不上: [], 遭遇被无视: [], 金币变化没交代: [], 原地踏步: [] };
  const latestByChar = new Map();
  for (const act of acts) {
    // ① 同伴最新一步在别处
    for (const mate of act.companions) {
      // 只有服务端也认为"就在附近"的人才算（提示词里列出来的），否则本来就是会被过滤掉的
      if (act.nearby.length && !act.nearby.includes(mate)) {
        continue;
      }
      const last = latestByChar.get(mate);
      if (last && last.location && act.location && last.location !== act.location) {
        issues['同伴位置对不上'].push(
          `${act.time} ${act.name} 在「${act.location}」与 ${mate} 同行，但 ${mate} 最新一步在「${last.location}」`);
      }
    }
    // ② 遭遇被无视
    if (act.encounter && !hasAny(act.actions, HANDLE_WORDS)) {
      issues['遭遇被无视'].push(`${act.time} ${act.name}：${act.encounter.slice(0, 50)}`);
    }
    // ③ 金币变化但没交代
    if (act.coinChange !== 0 && !hasAny(textOf(act), MONEY_WORDS)) {
      issues['金币变化没交代'].push(`${act.time} ${act.name} 金币 ${act.coinChange}，但叙述里没提钱`);
    }
    // ④ 原地踏步（与自己的上一步概括相同）
    const prev = latestByChar.get(act.name);
    if (prev && prev.summary && act.summary === prev.summary) {
      issues['原地踏步'].push(`${act.time} ${act.name}：概括与上一步完全相同（${act.summary.slice(0, 30)}）`);
    }
    latestByChar.set(act.name, act);
    act.companions.forEach((mate) => latestByChar.set(mate, latestByChar.get(mate)));
  }

  const total = Object.values(issues).reduce((sum, list) => sum + list.length, 0);
  return { file, acts: acts.length, issues, total, selfChecks, selfCheckFixed, selfCheckIssues };
}

/**
 * 解析模拟报告里的每一步（用的是**落库后的**字段：同行者经过服务端过滤、遭遇是服务端掷的）。
 * 报告格式：`- [MM-dd HH:mm] **名字** ｜ 地点 · 二级 ｜ N 分钟（原因） ｜ 耗时 ...`，后面跟几行明细。
 */
async function parseReport(file) {
  const text = await readFile(file, 'utf8');
  const steps = [];
  let current = null;
  for (const line of text.split('\n')) {
    const head = /^- \[(\d\d-\d\d \d\d:\d\d)\] \*\*([^*]+)\*\* ｜ ([^｜]+) ｜ ([^｜]+) ｜ 耗时 (\d+) ms \/ (\d+) 次调用/.exec(line);
    if (head) {
      const place = head[3].split('·');
      current = {
        time: head[1], name: head[2].trim(),
        location: place[0].trim(), sub: (place[1] || '').trim(),
        millis: Number(head[5]), calls: Number(head[6]),
        encounter: '', coins: 0, companions: '', summary: '', actions: ''
      };
      steps.push(current);
      continue;
    }
    if (!current) continue;
    const list = /^\s+- 运气：.*；遭遇：(.*)$/.exec(line);
    if (list) {
      current.encounter = list[1].trim() === '无' ? '' : list[1].trim();
      continue;
    }
    const stat = /^\s+- 伤势：.*金币变化 (-?\d+)；.*(?:；同行：(.*))?$/.exec(line);
    if (stat) {
      current.coins = Number(stat[1]);
      current.companions = (stat[2] || '').trim();
      continue;
    }
    const sum = /^\s+- (.+)$/.exec(line);
    if (sum && !current.summary && !/^(运气|伤势|委托板|委托|物品|纪闻|↳)/.test(sum[1])) {
      current.summary = sum[1].trim();
    }
  }
  return steps;
}

/** 把报告（落库后的字段）与 AI 日志（完整 actions）按 时间+角色 对上 */
async function auditPair(reportFile, logFile) {
  const steps = await parseReport(reportFile);
  const logAudit = await audit(logFile);
  const logActs = await collectLogActs(logFile);
  // 日志头里的时间是**真实墙钟**（不是模拟时间），所以不能用时间对——
  // 改成"按角色分组后按顺序配对"：同一个角色的第 i 步 ↔ 日志里该角色的第 i 次主调用。
  const byName = new Map();
  for (const act of logActs) {
    if (!byName.has(act.name)) byName.set(act.name, []);
    byName.get(act.name).push(act);
  }
  const cursor = new Map();
  let matched = 0;
  let rewritten = 0;
  for (const step of steps) {
    const list = byName.get(step.name) || [];
    const index = cursor.get(step.name) || 0;
    const act = list[index];
    cursor.set(step.name, index + 1);
    if (act) {
      step.actions = act.actions;
      matched++;
      // 落库的概括与模型原始输出的概括不一致 → 说明这一步被"每步自检"改写过了
      if (act.summary && step.summary && act.summary.trim() !== step.summary.trim()) {
        rewritten++;
      }
    }
  }
  return { reportFile, logFile, steps, matched, rewritten, selfChecks: logAudit.selfChecks, selfCheckFixed: logAudit.selfCheckFixed, selfCheckIssues: logAudit.selfCheckIssues };
}

async function collectLogActs(file) {
  const text = await readFile(file, 'utf8');
  const acts = [];
  for (const block of extractBlocks(text)) {
    if (!MAIN_ACTIONS.test(block.action)) continue;
    const json = finalJson(block.response);
    if (!json) continue;
    acts.push({ time: block.time, name: charName(block.chunk), actions: actionsText(json), summary: json.summary || '' });
  }
  return acts;
}

const args = process.argv.slice(2);
if (!args.length) {
  console.log('用法：node tools/scene-audit/audit-scene-log.mjs \\\n'
    + '  --report target/probe/scene-real-suspicious.md --log target/probe/ai-log-scene-real-suspicious.md \\\n'
    + '  --report target/probe/scene-real-always.md --log target/probe/ai-log-scene-real-always.md');
  process.exit(1);
}

const pairs = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] !== '--report' || !args[i + 1]) continue;
  const report = args[i + 1];
  let log = null;
  if (args[i + 2] === '--log') {
    log = args[i + 3];
    i += 3;
  } else {
    i += 1;
  }
  pairs.push({ report, log });
}

const summaryLines = [];
for (const pair of pairs) {
  if (!pair.report || !pair.log) continue;
  const auditResult = await auditPair(pair.report, pair.log);
  const steps = auditResult.steps;
  const issues = { 同伴位置对不上: [], 遭遇被无视: [], 金币变化没交代: [], 原地踏步: [] };
  const latest = new Map();
  const nearbyByKey = new Map();
  for (const act of await collectNearby(pair.log)) {
    nearbyByKey.set(`${act.time}|${act.name}`, act.nearby);
  }
  for (const step of steps) {
    for (const mate of step.companions.split(/[、,，]/).map((s) => s.trim()).filter(Boolean)) {
      const last = latest.get(mate);
      if (last && last.location && step.location && last.location !== step.location) {
        issues['同伴位置对不上'].push(`${step.time} ${step.name} 在「${step.location}」与 ${mate} 同行，但 ${mate} 最新一步在「${last.location}」`);
      }
    }
    if (step.encounter && !hasAny(step.actions || '', HANDLE_WORDS)) {
      issues['遭遇被无视'].push(`${step.time} ${step.name}：${step.encounter.slice(0, 50)}`);
    }
    if (step.coins !== 0 && !hasAny((step.actions || '') + step.summary, MONEY_WORDS)) {
      issues['金币变化没交代'].push(`${step.time} ${step.name} 金币 ${step.coins}`);
    }
    const prev = latest.get(step.name);
    if (prev && prev.summary && step.summary === prev.summary) {
      issues['原地踏步'].push(`${step.time} ${step.name}：${step.summary.slice(0, 30)}`);
    }
    latest.set(step.name, step);
  }
  const total = Object.values(issues).reduce((sum, list) => sum + list.length, 0);
  const ms = steps.map((s) => s.millis).filter((n) => n > 0).sort((a, b) => a - b);
  const avg = ms.length ? ms.reduce((a, b) => a + b, 0) / ms.length / 1000 : 0;
  const median = ms.length ? ms[Math.floor(ms.length / 2)] / 1000 : 0;
  const calls = steps.reduce((sum, s) => sum + s.calls, 0);
  console.log(`\n=== ${pair.report} ===`);
  console.log(`步数 ${steps.length}（与日志对上 ${auditResult.matched}）；总调用 ${calls}（平均 ${(calls / (steps.length || 1)).toFixed(2)} 次/步）`);
  console.log(`平均每步 ${avg.toFixed(1)} 秒（中位 ${median.toFixed(1)}）`);
  console.log(`自检调用 ${auditResult.selfChecks} 次，模型判定需要修正 ${auditResult.selfCheckFixed} 次`);
  console.log(`落库内容与模型原始输出不一致的步骤（= 真的被改写过）：${auditResult.rewritten} 步`);
  const uniqIssues = [...new Set(auditResult.selfCheckIssues)];
  uniqIssues.slice(0, 10).forEach((issue) => console.log('   · 自检提出问题：' + issue));
  console.log(`逻辑问题合计 ${total} 条`);
  for (const [kind, list] of Object.entries(issues)) {
    console.log(`- ${kind}：${list.length}`);
    list.slice(0, 3).forEach((item) => console.log('   · ' + item));
  }
  summaryLines.push({ file: pair.report, steps: steps.length, avg, median, calls, total, selfChecks: auditResult.selfChecks, fixed: auditResult.selfCheckFixed, issues });
}

console.log('\n=== 对比 ===');
for (const s of summaryLines) {
  console.log(`${s.file}：步数 ${s.steps}｜平均 ${s.avg.toFixed(1)} 秒｜自检 ${s.selfChecks} 次（修正 ${s.fixed}）｜逻辑问题 ${s.total} 条`);
}

async function collectNearby(file) {
  const text = await readFile(file, 'utf8');
  const out = [];
  for (const block of extractBlocks(text)) {
    if (!MAIN_ACTIONS.test(block.action)) continue;
    const json = finalJson(block.response);
    if (!json) continue;
    out.push({ time: block.time, name: charName(block.chunk), nearby: nearbyNames(block.chunk) });
  }
  return out;
}
