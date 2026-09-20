/**
 * 模板里调用的函数，必须在该组件的 <script setup> 里 import 或定义过。
 *
 * 为什么需要：这次就是栽在这——模板里写了 slotEmoji(item.slot)，
 * 但 import 列表里漏了 slotEmoji，运行时直接 TypeError（渲染中断 → 表单不更新）。
 * 这种错构建不会报、单元测试也测不到，只能这样静态查一遍。
 *
 * 用法：node vue-template-ident-check.mjs "E:/IdeaPooject/bcBlog/frontend/src/views/**\/*.vue"
 */
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';

async function collectVueFiles(root) {
  const out = [];
  async function walk(dir) {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) {
        await walk(full);
      } else if (entry.name.endsWith('.vue')) {
        out.push(full);
      }
    }
  }
  await walk(root);
  return out;
}

/** 模板里出现的 `foo(` 形式的调用名 */
const GLOBALS = new Set([
  // JS 内建
  'String', 'Number', 'Boolean', 'Object', 'Array', 'JSON', 'Date', 'RegExp', 'Math',
  'parseInt', 'parseFloat', 'isNaN', 'isFinite', 'encodeURIComponent', 'decodeURIComponent',
  // CSS 函数（常出现在 width="min(...)" 这类属性里）
  'min', 'max', 'clamp', 'calc', 'var', 'rgb', 'rgba', 'hsl', 'hsla', 'url', 'translate', 'translateX', 'translateY'
]);

function templateCalls(template) {
  const names = new Set();
  const re = /(?<![\w$.])([A-Za-z_$][\w$]*)\s*\(/g;
  let m;
  while ((m = re.exec(template))) {
    if (!GLOBALS.has(m[1])) {
      names.add(m[1]);
    }
  }
  return names;
}

const root = process.argv[2] || 'E:/IdeaPooject/bcBlog/frontend/src/views';
const files = await collectVueFiles(root);
let bad = 0;

for (const file of files) {
  const text = await readFile(file, 'utf8');
  const scriptAt = text.indexOf('<script setup');
  const templateEnd = scriptAt < 0 ? text.length : scriptAt;
  const template = text.slice(0, templateEnd);
  const script = scriptAt < 0 ? '' : text.slice(scriptAt);
  if (!script) {
    continue;
  }
  const missing = [];
  for (const name of templateCalls(template)) {
    // 定义过 / import 过 / Vue 内建 or JS 内建
    const defined = new RegExp(
      `function\\s+${name}\\s*\\(|const\\s+${name}\\s*=|let\\s+${name}\\s*=|var\\s+${name}\\s*=|\\b${name}\\b\\s*,|\\{\\s*${name}\\b|\\b${name}\\b\\s*\\}`
    ).test(script);
    if (!defined) {
      missing.push(name);
    }
  }
  if (missing.length) {
    bad++;
    console.log(`❌ ${file.replace(/\\/g, '/')}  → 模板里用了但脚本里没有：${missing.join('、')}`);
  }
}

console.log(bad ? `共 ${bad} 个文件有问题` : '✅ 所有模板调用都能在脚本里找到（import 或本地定义）');
