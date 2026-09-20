# Vue 模板调用检查（frontend/src/views）

## 这个工具解决什么问题

模板里写了 `{{ slotEmoji(item.slot) }}`，但组件的 `<script setup>` 里**忘了 import `slotEmoji`**：

- 构建**不报错**（Vite/Rollup 不管模板里的未定义标识符）；
- 单元测试也测不到；
- 只有真的渲染到那一行时才抛 `TypeError: _ctx.slotEmoji is not a function`，
  而 Vue 的这次更新会**整块中断**——表现是"接口明明返回了，表单却没填充、按钮一直转圈"。

2026-09-19 就真实踩到过这一次（沙盒「AI 生成并填充」在草稿里带装备时必现）。

## 用法

```bash
# 检查全部后台/前台页面
node tools/vue-template-check/check-template-idents.mjs frontend/src/views

# 只查某个子目录
node tools/vue-template-check/check-template-idents.mjs frontend/src/views/admin
```

输出示例：

```
❌ E:/.../frontend/src/views/admin/SandboxCharacter.vue  → 模板里用了但脚本里没有：slotEmoji
✅ 所有模板调用都能在脚本里找到（import 或本地定义）
```

## 说明

- 检查的是"模板里以 `foo(` 形式出现的调用名"，是否在 `<script setup>` 里被 `import` 或定义过；
- JS 内建（`String()`、`Number()`、`Math`…）与 CSS 函数（`min()`、`calc()`、`rgb()`…）已加白名单；
- 它不是编译器的替代品：`.vue` 改动后仍然要 `npm run build` 一次。
